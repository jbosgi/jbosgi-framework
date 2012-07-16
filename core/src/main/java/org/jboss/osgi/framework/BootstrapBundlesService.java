package org.jboss.osgi.framework;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

public abstract class BootstrapBundlesService<T> extends AbstractService<T> {

    private final ServiceName serviceName;

    public BootstrapBundlesService(ServiceName serviceName) {
        this.serviceName = serviceName;
    }

    public ServiceName getServiceName() {
        return serviceName;
    }

    protected void installCompleteService(ServiceTarget serviceTarget) {
        ServiceName targetName = serviceName.getParent().append("COMPLETE");
        new BootstrapBundlesComplete(targetName).install(serviceTarget);
    }
}