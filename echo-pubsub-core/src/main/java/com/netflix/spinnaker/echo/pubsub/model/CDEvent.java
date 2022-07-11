package com.netflix.spinnaker.echo.pubsub.model;

public class CDEvent {

  private int id;
  private String subject;

  public CDEvent() {
    // default Constructor
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  @Override
  public String toString() {
    return "CDEvent [id=" + id + ", subject=" + subject + "]";
  }
}
