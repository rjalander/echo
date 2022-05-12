package com.netflix.spinnaker.echo.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.model.pubsub.MessageDescription;
import com.netflix.spinnaker.echo.model.pubsub.PubsubSystem;
import com.netflix.spinnaker.echo.model.trigger.ManualEvent;
import com.netflix.spinnaker.echo.pipelinetriggers.PipelineCache;
import com.netflix.spinnaker.echo.pipelinetriggers.eventhandlers.ManualEventHandler;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService;
import com.netflix.spinnaker.echo.pipelinetriggers.orca.OrcaService.TriggerResponse;
import com.netflix.spinnaker.echo.pubsub.model.CDEvent;
import io.cloudevents.CloudEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  // TODO: EventTypes Should be taken from cdevents-sdk-java - dev.cdevents.CDEventsEnum once
  // integrated
  public static final String CD_ARTIFACT_PACKAGED_EVENT_TYPE = "cd.artifact.packaged.v1";
  public static final String CD_ARTIFACT_PUBLISHED_EVENT_TYPE = "cd.artifact.published.v1";
  public static final String CD_SERVICE_DEPLOYED_EVENT_TYPE = "cd.service.deployed.v1";
  private static final String CD_PIPELINERUN_FINISHED_EVENT_TYPE = "cd.pipelinerun.finished.v1";
  private static final String CD_PIPELINERUN_STARTED_EVENT_TYPE = "cd.pipelinerun.started.v1";

  private static final int retryCount = 5;
  private static final String EVENT_TYPE = "googleCloudBuild";
  private static final PubsubSystem pubsubSystem = PubsubSystem.GOOGLE;

  @Autowired ObjectMapper objectMapper;
  @Autowired OrcaService orca;
  @Autowired ManualEventHandler manualEventHandler;
  @Autowired PipelineCache pipelineCache;
  @Autowired CDEventCreator cdEventCreator;

  @RequestMapping(value = "/consume", method = RequestMethod.POST)
  public ResponseEntity<Void> consumeEvent(@RequestBody CloudEvent inputEvent)
      throws IOException, TimeoutException {
    if (inputEvent.getType().equals(CD_PIPELINERUN_FINISHED_EVENT_TYPE)) {
      System.out.println("Received Event with type - " + CD_PIPELINERUN_FINISHED_EVENT_TYPE);
      log.info("Received Event with type - " + CD_PIPELINERUN_FINISHED_EVENT_TYPE);

    } else if (inputEvent.getType().equals(CD_PIPELINERUN_STARTED_EVENT_TYPE)) {
      System.out.println("Received Event with type - " + CD_PIPELINERUN_STARTED_EVENT_TYPE);
      log.info("Received Event with type - " + CD_PIPELINERUN_STARTED_EVENT_TYPE);

    } else if (inputEvent.getType().equals(CD_SERVICE_DEPLOYED_EVENT_TYPE)) {
      System.out.println("Received Event with type - " + CD_SERVICE_DEPLOYED_EVENT_TYPE);
      log.info("Received Event with type - " + CD_SERVICE_DEPLOYED_EVENT_TYPE);

    } else if (inputEvent.getType().equals(CD_ARTIFACT_PUBLISHED_EVENT_TYPE)) {
      System.out.println("Received Event with type - " + CD_ARTIFACT_PUBLISHED_EVENT_TYPE);
      log.info("Received Event with type - " + CD_ARTIFACT_PUBLISHED_EVENT_TYPE);
      Event event = createEvent(createMessageDescription());
      ManualEvent manualEvent = manualEventHandler.convertEvent(event);
      // List<Pipeline> pipeLines = manualEventHandler.getMatchingPipelines(manualEvent,
      // pipelineCache);
      List<Pipeline> pipeLines = pipelineCache.getPipelinesSync();
      for (Pipeline pipeline : pipeLines) {
        System.out.println("Pipeline from pipelineCache - " + pipeline);
        if (pipeline.toString().contains("poc")
            && pipeline.toString().contains("deploy-spinnaker-poc")) {
          log.info("Found Matching pipeline {}", pipeline);
          TriggerResponse response = triggerWithRetries(pipeline);
          log.info(
              "Successfully triggered pipeline: {} with execution id: {}",
              pipeline,
              response.getRef());
          cdEventCreator.createPipelineRunStartedEvent();
          // TODO: Mark as finished on some condition
          cdEventCreator.createPipelineRunFinishedEvent(); // OR -
          cdEventCreator.createServiceDeployedEvent(); // - received by Keptn
        }
      }
    } else {
      throw new IllegalStateException(
          "Error: Un supported Event type received " + inputEvent.getType() + "\"");
    }

    CDEvent data = objectMapper.readValue(inputEvent.getData().toBytes(), CDEvent.class);
    System.out.println("CDEvent getID --> " + data.getId());
    System.out.println("CDEvent getSubject --> " + data.getSubject());
    return ResponseEntity.ok().build();
  }

  @Value("${BROKER_SINK:http://localhost:8090/default/events-broker}")
  private String BROKER_SINK;

  @RequestMapping(value = "/produce", method = RequestMethod.POST)
  public ResponseEntity<Void> produceEvent() throws IOException, TimeoutException {
    log.info("produceEvent() : for BROKER_SINK URL - {}", BROKER_SINK);
    CDEvent data = new CDEvent();
    data.setId(123);
    data.setSubject("cdevent");
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    CloudEvent cloudEvent =
        cdEventCreator.createArtifactEvent(
            CD_ARTIFACT_PACKAGED_EVENT_TYPE,
            "123",
            "produce_artifact",
            "1.0",
            objectMapper.writeValueAsString(data));
    cdEventCreator.sendCloudEvent(cloudEvent);
    log.info("produceEvent() : Done for BROKER_SINK URL - {}", BROKER_SINK);
    return ResponseEntity.ok().build();
  }

  public Event createEvent(MessageDescription description) {
    log.info("Processing pubsub event with payload {}", description.getMessagePayload());

    var event = new Event();
    Map<String, Object> content = new HashMap<>();
    content.put("messageDescription", description);

    Metadata details = new Metadata();
    details.setType(EVENT_TYPE);

    event.setContent(content);
    event.setDetails(details);
    return event;
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
        // registry.counter("orca.trigger.retries").increment();
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

  private MessageDescription createMessageDescription() {
    Map<String, String> messageAttributes = new HashMap<>();
    MessageDescription description =
        MessageDescription.builder()
            .subscriptionName("poc_subscription")
            .messagePayload("poc_payload")
            .messageAttributes(messageAttributes)
            .pubsubSystem(pubsubSystem)
            .ackDeadlineSeconds(60) // Set a high upper bound on message processing time.
            .retentionDeadlineSeconds(
                7 * 24 * 60 * 60) // Expire key after max retention time, which is 7 days.
            .build();
    return description;
  }

  @RequestMapping(value = "/consume", method = RequestMethod.GET)
  public ResponseEntity<Void> handleGetRequest() {
    System.out.println("CDEvent Consumer API is running !!");
    return ResponseEntity.ok().build();
  }
}
