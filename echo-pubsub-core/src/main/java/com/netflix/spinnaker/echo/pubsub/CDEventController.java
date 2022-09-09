package com.netflix.spinnaker.echo.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService.TriggerResponse;
import com.netflix.spinnaker.echo.pubsub.model.CDEvent;
import dev.cdevents.CDEventEnums;
import dev.cdevents.CDEventTypes;
import io.cloudevents.CloudEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import retrofit.RetrofitError;
import retrofit.RetrofitError.Kind;
import retrofit.client.Response;

@RestController
@RequestMapping(value = "/cdevent")
@Slf4j
public class CDEventController {

  public static final String CD_ARTIFACT_PACKAGED_EVENT_TYPE =
      CDEventEnums.ArtifactPackagedEventV1.getEventType();
  public static final String CD_ARTIFACT_PUBLISHED_EVENT_TYPE =
      CDEventEnums.ArtifactPublishedEventV1.getEventType();
  public static final String CD_SERVICE_DEPLOYED_EVENT_TYPE =
      CDEventEnums.ServiceDeployedEventV1.getEventType();
  public static final String CD_PIPELINERUN_FINISHED_EVENT_TYPE =
      CDEventEnums.PipelineRunFinishedEventV1.getEventType();
  public static final String CD_PIPELINERUN_STARTED_EVENT_TYPE =
      CDEventEnums.PipelineRunStartedEventV1.getEventType();

  private static final int retryCount = 5;

  @Autowired ObjectMapper objectMapper;
  @Autowired OrcaService orca;
  @Autowired PipelineCache pipelineCache;
  @Autowired CDEventCreator cdEventCreator;

  @RequestMapping(value = "/consume", method = RequestMethod.POST)
  public ResponseEntity<Void> consumeEvent(@RequestBody CloudEvent inputEvent) {
    log.info(
        "CDEventEnums.ArtifactPackagedEventV1.getEventType() --> "
            + CDEventEnums.ArtifactPackagedEventV1.getEventType());
    if (inputEvent.getType().equals(CDEventEnums.ArtifactPackagedEventV1.getEventType())) {
      log.info("Received Event with type - " + CD_ARTIFACT_PACKAGED_EVENT_TYPE);

      try {

        String artifactId = inputEvent.getExtension("artifactid").toString();
        String artifactName = inputEvent.getExtension("artifactname").toString();
        log.info("Received latest artifactid from input event {} ", artifactId);

        String contextId = "";
        String triggerId = "";
        String ceDataJsonString =
            new String(inputEvent.getData().toBytes(), StandardCharsets.UTF_8);
        Map<String, Object> ceDataMap = objectMapper.readValue(ceDataJsonString, HashMap.class);
        if (ceDataMap.get("shkeptncontext") != null && ceDataMap.get("triggerid") != null) {
          contextId = (String) ceDataMap.get("shkeptncontext");
          triggerId = (String) ceDataMap.get("triggerid");
          log.info("Received contextId - {}, triggerId - {}", contextId, triggerId);
        }

        triggerPipelineWithArtifactData(artifactId, artifactName, contextId, triggerId);
      } catch (Exception e) {
        log.error(
            "Exception occured while proceesing cdevent/consume request. {} ", e.getMessage());
      }
    } else {
      throw new IllegalStateException(
          "Error: Un supported Event type received " + inputEvent.getType() + "\"");
    }

    return ResponseEntity.ok().build();
  }

  private void triggerPipelineWithArtifactData(
      String artifactId, String artifactName, String contextId, String triggerId)
      throws TimeoutException {
    String artifactImage = artifactId.replace("kind-registry", "localhost");
    pipelineCache
        .getPipelinesSync()
        .forEach(
            pipeline -> {
              log.info("Pipeline from pipelineCache - {}", pipeline);
              if (pipeline.getName().equals(("deploy-spinnaker-poc"))) {
                log.info("Found Matching pipeline {}", pipeline);

                List<Map<String, Object>> stageList = pipeline.getStages();
                Optional<Map<String, Object>> deployStageMap =
                    stageList.stream()
                        .filter(
                            stageMap ->
                                stageMap.get("type").toString().equalsIgnoreCase("deployManifest"))
                        .findFirst();

                if (deployStageMap.isPresent()) {
                  List<Map<String, Object>> manifestList =
                      (List<Map<String, Object>>) deployStageMap.get().get("manifests");
                  Optional<Map<String, Object>> deployManifestMap =
                      manifestList.stream()
                          .filter(
                              manifestMap ->
                                  manifestMap.get("kind").toString().equalsIgnoreCase("Deployment"))
                          .findFirst();
                  if (deployManifestMap.isPresent()) {
                    Map<String, Object> specMap =
                        (Map<String, Object>) deployManifestMap.get().get("spec");
                    Map<String, Object> templateMap = (Map<String, Object>) specMap.get("template");
                    Map<String, Object> innerSpecMap =
                        (Map<String, Object>) templateMap.get("spec");
                    List<Map<String, Object>> containersList =
                        (List<Map<String, Object>>) innerSpecMap.get("containers");
                    containersList.forEach(
                        containersMap -> {
                          if (containersMap
                              .get("name")
                              .toString()
                              .equalsIgnoreCase("cdevents-spinnaker-poc")) {
                            containersMap.replace("image", artifactImage);
                            log.info(
                                "Replaced container image from artifact data with {}",
                                artifactImage);
                          }
                        });
                  }
                }

                TriggerResponse response = triggerWithRetries(pipeline);
                log.info(
                    "Successfully triggered pipeline: {} with execution id: {}",
                    pipeline,
                    response.getRef());
                try {
                  cdEventCreator.createPipelineRunStartedEvent(pipeline, artifactId, artifactName);
                  // TODO: Mark as finished on checking on pipelinerun status
                  log.info("Wait for 30 Sec to Finish the pipeline run");
                  Thread.sleep(30000);
                  cdEventCreator.createPipelineRunFinishedEvent(
                      pipeline, artifactId, artifactName); // OR -
                  // cdEventCreator.createServiceDeployedEvent(pipeline, contextId, triggerId);
                } catch (Exception e) {
                  log.error("Exception occured while creating cdevent, {} ", e.getMessage());
                }
              }
            });
  }

  @Value("${BROKER_SINK:http://localhost:8090/default/events-broker}")
  private String BROKER_SINK;

  @RequestMapping(value = "/produce", method = RequestMethod.POST)
  public ResponseEntity<Void> produceEvent() throws IOException, TimeoutException {
    log.info("produceEvent() : for BROKER_SINK URL - {}", BROKER_SINK);
    CDEvent data = new CDEvent();
    data.setEventId("123");
    data.setSubject("cdevent");
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    CloudEvent cloudEvent =
        CDEventTypes.createArtifactEvent(
            CD_ARTIFACT_PACKAGED_EVENT_TYPE,
            "123",
            "produce_artifact",
            "1.0",
            objectMapper.writeValueAsString(data));
    cdEventCreator.sendCloudEvent(cloudEvent);
    log.info("produceEvent() : Done for BROKER_SINK URL - {}", BROKER_SINK);
    return ResponseEntity.ok().build();
  }

  private TriggerResponse triggerWithRetries(Pipeline pipeline) {
    int attempts = 0;

    while (true) {
      try {
        attempts++;
        return orca.trigger(pipeline);
      } catch (RetrofitError e) {
        if ((attempts >= retryCount) || !isRetryableError(e)) {
          throw e;
        } else {
          log.warn(
              "Error triggering {} with {} (attempt {}/{}). Retrying...",
              pipeline,
              e,
              attempts,
              retryCount);
        }
      }

      try {
        Thread.sleep(5000);
      } catch (InterruptedException ignored) {
      }
    }
  }

  private static boolean isRetryableError(Throwable error) {
    if (!(error instanceof RetrofitError)) {
      return false;
    }
    RetrofitError retrofitError = (RetrofitError) error;

    if (retrofitError.getKind() == Kind.NETWORK) {
      return true;
    }

    if (retrofitError.getKind() == Kind.HTTP) {
      Response response = retrofitError.getResponse();
      return (response != null && response.getStatus() != HttpStatus.BAD_REQUEST.value());
    }

    return false;
  }

  @RequestMapping(value = "/consume", method = RequestMethod.GET)
  public ResponseEntity<Void> handleGetRequest() {
    System.out.println("CDEvent Consumer API is running !!");
    return ResponseEntity.ok().build();
  }
}
