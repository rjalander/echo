package com.netflix.spinnaker.echo.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.echo.model.Pipeline;
import com.netflix.spinnaker.echo.pubsub.model.CDEvent;
import dev.cdevents.CDEventEnums;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.message.MessageWriter;
import io.cloudevents.http.HttpMessageFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CDEventCreator {

  private static final String CD_PIPELINERUN_FINISHED_EVENT_TYPE =
      CDEventEnums.PipelineRunFinishedEventV1.getEventType();
  private static final String CD_PIPELINERUN_STARTED_EVENT_TYPE =
      CDEventEnums.PipelineRunStartedEventV1.getEventType();
  public static final String CD_ARTIFACT_PACKAGED_EVENT_TYPE =
      CDEventEnums.ArtifactPackagedEventV1.getEventType();
  public static final String CD_SERVICE_DEPLOYED_EVENT_TYPE =
      CDEventEnums.ServiceDeployedEventV1.getEventType();

  @Autowired ObjectMapper objectMapper;

  @Value(
      "${BROKER_SINK:http://broker-ingress.knative-eventing.svc.cluster.local/default/events-broker}")
  private String BROKER_SINK;

  public void createServiceDeployedEvent(Pipeline pipeline, String contextId, String triggerId)
      throws IOException {
    log.info("Create ServiceDeployed event and send to events-broker URL - {}", BROKER_SINK);
    CDEvent data = new CDEvent();
    data.setPipelineId(pipeline.getId());
    data.setPipelineName(pipeline.getName());
    data.setContextId(contextId);
    data.setTriggerId(triggerId);
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    // TODO : will be invoked from sdk-java later -
    // dev.cdevents.CDEventTypes.createPipelineRunEvent(
    CloudEvent cloudEvent =
        dev.cdevents.CDEventTypes.createServiceEvent(
            CD_SERVICE_DEPLOYED_EVENT_TYPE,
            "serviceId",
            "poc",
            "serviceVersion",
            objectMapper.writeValueAsString(data));
    sendCloudEvent(cloudEvent);
    log.info("cloudEvent service deployed Data {} ", cloudEvent.getData());
    log.info("ServiceDeployed event sent to events-broker URL - {}", BROKER_SINK);
  }

  public CloudEvent createPipelineRunStartedEvent() throws IOException {
    log.info("Create PipelineRunStarted event and send to events-broker URL - {}", BROKER_SINK);
    CDEvent data = new CDEvent();
    data.setPipelineId("123");
    data.setSubject("PipelineRunStarted");
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    // TODO : will be invoked from sdk-java later -
    // dev.cdevents.CDEventTypes.createPipelineRunEvent();
    CloudEvent cloudEvent =
        dev.cdevents.CDEventTypes.createPipelineRunEvent(
            CD_PIPELINERUN_STARTED_EVENT_TYPE,
            "pipelineRunId",
            "pipelineRunName",
            "pipelineRunStatus",
            "pipelineRunURL",
            "pipelineRunErrors",
            objectMapper.writeValueAsString(data));
    // sendCloudEvent(cloudEvent);
    log.info("PipelineRunStarted event sent to events-broker URL - {}", BROKER_SINK);
    return cloudEvent;
  }

  public void createPipelineRunFinishedEvent() throws IOException {
    log.info("Create PipelineRunFinished event and send to events-broker URL - {}", BROKER_SINK);
    CDEvent data = new CDEvent();
    data.setPipelineId("123");
    data.setSubject("PipelineRunFinished");
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    // TODO : will be invoked from sdk-java later -
    // dev.cdevents.CDEventTypes.createPipelineRunEvent();
    CloudEvent cloudEvent =
        dev.cdevents.CDEventTypes.createPipelineRunEvent(
            CD_PIPELINERUN_FINISHED_EVENT_TYPE,
            "pipelineRunId",
            "pipelineRunName",
            "pipelineRunStatus",
            "pipelineRunURL",
            "pipelineRunErrors",
            objectMapper.writeValueAsString(data));
    sendCloudEvent(cloudEvent);
    log.info("PipelineRunFinished event sent to events-broker URL - {}", BROKER_SINK);
  }

  public void createArtifactPackagedEvent() throws IOException {
    log.info("Create ArtifactPackaged event and send to events-broker URL - {}", BROKER_SINK);
    CDEvent data = new CDEvent();
    data.setPipelineId("123");
    data.setSubject("ArtifactPackaged");
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    // TODO : will be invoked from sdk-java later - dev.cdevents.CDEventTypes.createArtifactEvent();
    CloudEvent cloudEvent =
        dev.cdevents.CDEventTypes.createArtifactEvent(
            CD_ARTIFACT_PACKAGED_EVENT_TYPE,
            "artifactId",
            "artifactName",
            "artifactVersion",
            objectMapper.writeValueAsString(data));
    sendCloudEvent(cloudEvent);
    log.info("ArtifactPackaged event sent to events-broker URL - {}", BROKER_SINK);
  }

  public void sendCloudEvent(CloudEvent ceToSend) throws IOException {
    URL url = new URL(BROKER_SINK);
    HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
    httpUrlConnection.setRequestMethod("POST");
    httpUrlConnection.setDoOutput(true);
    httpUrlConnection.setDoInput(true);
    MessageWriter messageWriter = createMessageWriter(httpUrlConnection);
    messageWriter.writeBinary(ceToSend);

    if (httpUrlConnection.getResponseCode() / 100 != 2) {
      throw new RuntimeException(
          "Failed : HTTP error code : " + httpUrlConnection.getResponseCode());
    }
  }

  private MessageWriter createMessageWriter(HttpURLConnection httpUrlConnection) {
    return HttpMessageFactory.createWriter(
        httpUrlConnection::setRequestProperty,
        body -> {
          try {
            if (body != null) {
              httpUrlConnection.setRequestProperty("content-length", String.valueOf(body.length));
              try (OutputStream outputStream = httpUrlConnection.getOutputStream()) {
                outputStream.write(body);
              }
            } else {
              httpUrlConnection.setRequestProperty("content-length", "0");
            }
          } catch (IOException t) {
            throw new UncheckedIOException(t);
          }
        });
  }
}
