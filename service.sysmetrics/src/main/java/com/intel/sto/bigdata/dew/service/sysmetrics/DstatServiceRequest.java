package com.intel.sto.bigdata.dew.service.sysmetrics;

import com.intel.sto.bigdata.dew.message.ServiceRequest;

public class DstatServiceRequest extends ServiceRequest {

  private long startTime;
  private long endTime;

  public DstatServiceRequest(long startTime, long endTime) {
    super("dstatService", "get");
  }

  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public void setEndTime(long endTime) {
    this.endTime = endTime;
  }

}