package com.intel.sto.bigdata.dew.agent;

import java.io.IOException;
import java.net.URL;

import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.Identify;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import com.intel.sto.bigdata.dew.message.AgentRegister;
import com.intel.sto.bigdata.dew.message.ServiceRequest;
import com.intel.sto.bigdata.dew.message.ServiceResponse;
import com.intel.sto.bigdata.dew.message.StartService;
import com.intel.sto.bigdata.dew.service.Service;
import com.intel.sto.bigdata.dew.service.ServiceDes;
import com.intel.sto.bigdata.dew.utils.Host;
import com.intel.sto.bigdata.dew.utils.Util;

public class Agent extends UntypedActor {
  private String masterUrl;
  private ActorRef master;
  private ServiceManager serviceManager;
  private ServiceDes defaultServiceDes;
  private LoggingAdapter log = Logging.getLogger(this);

  public Agent(String masterUrl, ServiceManager serviceManager, String serviceDes) {
    this.serviceManager = serviceManager;
    this.masterUrl = masterUrl;
    if (serviceDes != null) {
      defaultServiceDes = new ServiceDes();
      defaultServiceDes.deSerialize(serviceDes);// TODO exit if failed.
      processStartService(defaultServiceDes);
    }
    sendIdentifyRequest();
  }

  private void sendIdentifyRequest() {
    log.info("Connect master:" + masterUrl);
    getContext().actorSelection(masterUrl).tell(new Identify(masterUrl), getSelf());
    // getContext()
    // .system()
    // .scheduler()
    // .scheduleOnce(Duration.create(5, SECONDS), getSelf(), ReceiveTimeout.getInstance(),
    // getContext().dispatcher(), getSelf());
  }

  @Override
  public void onReceive(Object message) throws Exception {
    if (message instanceof ActorIdentity) {
      master = ((ActorIdentity) message).getRef();
      if (master == null) {
        log.error("Master not available: " + masterUrl);
      } else {
        // getContext().watch(master);
        // getContext().become(active, true);
        master.tell(new AgentRegister(Host.getIp(), Host.getName(), 0), getSelf());
      }
    } else if (message instanceof ServiceRequest) {
      ServiceRequest serviceRequest = (ServiceRequest) message;
      Service service = serviceManager.getService(serviceRequest.getServiceName());
      if (service != null && serviceRequest.getServiceMethod().equals("get")) {
        ServiceResponse sr = service.get(message);
        sr.setNodeName(Host.getName());
        sr.setIp(Host.getIp());
        getSender().tell(sr, getSelf());
      }
    } else if (message instanceof ServiceDes) {
      if (defaultServiceDes == null) {
        String serviceName = processStartService(message);
        if (serviceName != null) {
          getSender().tell(new StartService(serviceName, null), getSelf());
        }
      } else {// the agent only start one service (defalutServiceDes)
        getSender().tell(new StartService(defaultServiceDes.getServiceName(), null), getSelf());
      }
    } else {
      log.warning("Unhandled message:" + message);
      unhandled(message);
    }
  }

  private String processStartService(Object message) {
    ServiceDes sd = (ServiceDes) message;

    if (sd.getServiceType().toLowerCase().equals("thread")) {
      ClassLoader cl = this.getClass().getClassLoader();
      try {
        Service service = (Service) cl.loadClass(sd.getServiceClass()).newInstance();
        serviceManager.putService(sd.getServiceName(), service);
        new Thread(service).start();
      } catch (Exception e) {
        e.printStackTrace();
      }
      return sd.getServiceName();
    }
    if (sd.getServiceType().toLowerCase().equals("process")) {
      sd.setServiceType("thread");
      String cp = Util.findDewClassPath();
      if (cp != null) {
        System.out.println("-----------" + cp);
      }

      String des = sd.serialize();
      Runtime runtime = Runtime.getRuntime();
      try {
        Process process =
            runtime.exec("java -cp " + cp + " com.intel.sto.bigdata.dew.agent.DewDrop " + masterUrl
                + " " + des);
        serviceManager.putProcess(sd.getServiceName(), process);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return null;

  }

}
