package com.netflix.spinnaker.echo.pubsub.model;

public class CDEvent {

  private String eventId;
  private String eventName;
  private String contextId;
  private String triggerId;
  private String subject;

  private String artifactId;

  private String artifactName;

  public CDEvent() {
    // default Constructor
  }

  /** @return the eventId */
  public String getEventId() {
    return eventId;
  }

  /** @param eventId the eventId to set */
  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  /** @return the eventName */
  public String getEventName() {
    return eventName;
  }

  /** @param eventName the eventName to set */
  public void setEventName(String eventName) {
    this.eventName = eventName;
  }

  /** @return the contextId */
  public String getContextId() {
    return contextId;
  }

  /** @param contextId the contextId to set */
  public void setContextId(String contextId) {
    this.contextId = contextId;
  }

  /** @return the triggerId */
  public String getTriggerId() {
    return triggerId;
  }

  /** @param triggerId the triggerId to set */
  public void setTriggerId(String triggerId) {
    this.triggerId = triggerId;
  }

  /** @return the subject */
  public String getSubject() {
    return subject;
  }

  /** @param subject the subject to set */
  public void setSubject(String subject) {
    this.subject = subject;
  }

  /** @return the artifactId */
  public String getArtifactId() {
    return artifactId;
  }

  /** @param artifactId the artifactId to set */
  public void setArtifactId(String artifactId) {
    this.artifactId = artifactId;
  }

  /** @return the artifactName */
  public String getArtifactName() {
    return artifactName;
  }

  /** @param artifactName the artifactName to set */
  public void setArtifactName(String artifactName) {
    this.artifactName = artifactName;
  }

  @Override
  public String toString() {
    return "CDEvent [eventId="
        + eventId
        + ", eventName="
        + eventName
        + ", contextId="
        + contextId
        + ", triggerId="
        + triggerId
        + ", subject="
        + subject
        + ", artifactId="
        + artifactId
        + ", artifactName="
        + artifactName
        + "]";
  }
}
