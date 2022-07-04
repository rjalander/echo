package com.netflix.spinnaker.echo.pubsub.model;

public class CDEvent {

  private String pipelineId;
  private String pipelineName;
  private String pipelineResults;
  private String contextId;
  private String triggerId;
  private String subject;

  public CDEvent() {
    // default Constructor
  }

  public String getPipelineId() {
    return pipelineId;
  }

  public void setPipelineId(String pipelineId) {
    this.pipelineId = pipelineId;
  }

  public String getPipelineName() {
    return pipelineName;
  }

  public void setPipelineName(String pipelineName) {
    this.pipelineName = pipelineName;
  }

  public String getPipelineResults() {
    return pipelineResults;
  }

  public void setPipelineResults(String pipelineResults) {
    this.pipelineResults = pipelineResults;
  }

  public String getContextId() {
    return contextId;
  }

  public void setContextId(String contextId) {
    this.contextId = contextId;
  }

  public String getTriggerId() {
    return triggerId;
  }

  public void setTriggerId(String triggerId) {
    this.triggerId = triggerId;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  @Override
  public String toString() {
    return "CDEvent [pipelineId="
        + pipelineId
        + ", pipelineName="
        + pipelineName
        + ", pipelineResults="
        + pipelineResults
        + ", contextId="
        + contextId
        + ", triggerId="
        + triggerId
        + ", subject="
        + subject
        + "]";
  }
}
