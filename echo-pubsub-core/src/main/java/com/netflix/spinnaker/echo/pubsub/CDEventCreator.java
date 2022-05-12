package com.netflix.spinnaker.echo.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.echo.pubsub.model.CDEvent;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.message.MessageWriter;
import io.cloudevents.core.v03.CloudEventBuilder;
import io.cloudevents.http.HttpMessageFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CDEventCreator {

  // TODO: EventTypes Should be taken from cdevents-sdk-java - dev.cdevents.CDEventsEnum once
  // integrated
  private static final String CD_PIPELINERUN_FINISHED_EVENT_TYPE = "cd.pipelinerun.finished.v1";
  private static final String CD_PIPELINERUN_STARTED_EVENT_TYPE = "cd.pipelinerun.started.v1";
  public static final String CD_ARTIFACT_PACKAGED_EVENT_TYPE = "cd.artifact.packaged.v1";
  public static final String CD_SERVICE_DEPLOYED_EVENT_TYPE = "cd.service.deployed.v1";

  @Autowired ObjectMapper objectMapper;

  @Value(
      "${BROKER_SINK:http://broker-ingress.knative-eventing.svc.cluster.local/default/events-broker}")
  private String BROKER_SINK;

  public void createServiceDeployedEvent() throws IOException {
    log.info("Create ServiceDeployed event and send to events-broker URL - {}", BROKER_SINK);
    CDEvent data = new CDEvent();
    data.setId(123);
    data.setSubject("ServiceDeployed");
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    // TODO : will be invoked from sdk-java later -
    // dev.cdevents.CDEventTypes.createPipelineRunEvent();
    CloudEvent cloudEvent =
        createServiceEvent(
            CD_SERVICE_DEPLOYED_EVENT_TYPE,
            "serviceId",
            "serviceName",
            "serviceVersion",
            objectMapper.writeValueAsString(data));
    sendCloudEvent(cloudEvent);
    log.info("ServiceDeployed event sent to events-broker URL - {}", BROKER_SINK);
  }

  public void createPipelineRunStartedEvent() throws IOException {
    log.info("Create PipelineRunStarted event and send to events-broker URL - {}", BROKER_SINK);
    CDEvent data = new CDEvent();
    data.setId(123);
    data.setSubject("PipelineRunStarted");
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    // TODO : will be invoked from sdk-java later -
    // dev.cdevents.CDEventTypes.createPipelineRunEvent();
    CloudEvent cloudEvent =
        createPipelineRunEvent(
            CD_PIPELINERUN_STARTED_EVENT_TYPE,
            "pipelineRunId",
            "pipelineRunName",
            "pipelineRunStatus",
            "pipelineRunURL",
            "pipelineRunErrors",
            objectMapper.writeValueAsString(data));
    sendCloudEvent(cloudEvent);
    log.info("PipelineRunStarted event sent to events-broker URL - {}", BROKER_SINK);
  }

  public void createPipelineRunFinishedEvent() throws IOException {
    log.info("Create PipelineRunFinished event and send to events-broker URL - {}", BROKER_SINK);
    CDEvent data = new CDEvent();
    data.setId(123);
    data.setSubject("PipelineRunFinished");
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    // TODO : will be invoked from sdk-java later -
    // dev.cdevents.CDEventTypes.createPipelineRunEvent();
    CloudEvent cloudEvent =
        createPipelineRunEvent(
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
    data.setId(123);
    data.setSubject("ArtifactPackaged");
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    // TODO : will be invoked from sdk-java later - dev.cdevents.CDEventTypes.createArtifactEvent();
    CloudEvent cloudEvent =
        createArtifactEvent(
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

  // TODO : will be removed once after cdevents-sdk-java integrated to echo project
  public static CloudEvent createServiceEvent(
      final String serviceEventType,
      final String serviceId,
      final String serviceName,
      final String serviceVersion,
      final String serviceData) {
    CloudEvent ceToSend =
        buildCloudEvent(serviceEventType, serviceData)
            .withExtension("serviceid", serviceId)
            .withExtension("servicename", serviceName)
            .withExtension("serviceversion", serviceVersion)
            .build();
    return ceToSend;
  }

  // TODO : will be removed once after cdevents-sdk-java integrated to echo project
  public CloudEvent createArtifactEvent(
      final String artifactEventType,
      final String artifactId,
      final String artifactName,
      final String artifactVersion,
      final String artifactData) {
    CloudEvent ceToSend =
        buildCloudEvent(artifactEventType, artifactData)
            .withExtension("artifactid", artifactId)
            .withExtension("artifactname", artifactName)
            .withExtension("artifactversion", artifactVersion)
            .build();
    return ceToSend;
  }

  // TODO : will be removed once after cdevents-sdk-java integrated to echo project
  private static CloudEvent createPipelineRunEvent(
      final String pipelineRunEventType,
      final String pipelineRunId,
      final String pipelineRunName,
      final String pipelineRunStatus,
      final String pipelineRunURL,
      final String pipelineRunErrors,
      final String pipelineRunData) {
    CloudEvent ceToSend =
        buildCloudEvent(pipelineRunEventType, pipelineRunData)
            .withExtension("pipelinerunid", pipelineRunId)
            .withExtension("pipelinerunname", pipelineRunName)
            .withExtension("pipelinerunstatus", pipelineRunStatus)
            .withExtension("pipelinerunurl", pipelineRunURL)
            .withExtension("pipelinerunerrors", pipelineRunErrors)
            .build();
    return ceToSend;
  }

  // TODO : will be removed once after cdevents-sdk-java integrated to echo project
  private static CloudEventBuilder buildCloudEvent(final String eventType, final String eventData) {
    CloudEventBuilder ceBuilder =
        new CloudEventBuilder()
            .withId(UUID.randomUUID().toString())
            .withSource(URI.create("cdevents-sdk-java"))
            .withType(eventType)
            // TODO: fix to set ContentType in cdevents-sdk-java
            .withDataContentType("application/json; charset=UTF-8")
            .withData(eventData.getBytes(StandardCharsets.UTF_8))
            .withTime(OffsetDateTime.now());
    return ceBuilder;
  }
}
