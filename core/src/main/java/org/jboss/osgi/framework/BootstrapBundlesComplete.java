package org.jboss.osgi.framework;

import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

public class BootstrapBundlesComplete extends AbstractService<Void> {

    public void install(ServiceTarget serviceTarget, ServiceName serviceName) {
        BootstrapBundlesComplete service = new BootstrapBundlesComplete();
        ServiceBuilder<Void> builder = serviceTarget.addService(serviceName, service);
        addServiceDependencies(builder);
        builder.install();
    }

    protected void addServiceDependencies(ServiceBuilder<Void> builder) {
    }
}