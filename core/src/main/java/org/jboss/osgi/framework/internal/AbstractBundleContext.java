/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.osgi.framework.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.BundleInstallProvider;
import org.jboss.osgi.framework.FutureServiceValue;
import org.jboss.osgi.vfs.AbstractVFS;
import org.jboss.osgi.vfs.VFSUtils;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

/**
 * The base of all {@link BundleContext} implementations.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
abstract class AbstractBundleContext implements BundleContext {

    private final AbstractBundleState bundleState;
    private boolean destroyed;

    AbstractBundleContext(AbstractBundleState bundleState) {
        if (bundleState == null)
            throw new IllegalArgumentException("Null bundleState");

        this.bundleState = bundleState;
    }

    /**
     * Assert that the given context is an instance of AbstractBundleContext
     *
     * @throws IllegalArgumentException if the given context is not an instance of AbstractBundleContext
     */
    static AbstractBundleContext assertBundleContext(BundleContext context) {
        if (context == null)
            throw new IllegalArgumentException("Null bundle");

        if (context instanceof AbstractBundleContext == false)
            throw new IllegalArgumentException("Not an AbstractBundleContext: " + context);

        return (AbstractBundleContext) context;
    }

    void destroy() {
        destroyed = true;
    }

    AbstractBundleState getBundleState() {
        return bundleState;
    }

    BundleManager getBundleManager() {
        return bundleState.getBundleManager();
    }

    FrameworkState getFrameworkState() {
        return bundleState.getFrameworkState();
    }

    @Override
    public String getProperty(String key) {
        checkValidBundleContext();
        getBundleManager().assertFrameworkActive();
        Object value = getBundleManager().getProperty(key);
        return (value instanceof String ? (String) value : null);
    }

    @Override
    public Bundle getBundle() {
        checkValidBundleContext();
        return bundleState;
    }

    @Override
    public Bundle installBundle(String location) throws BundleException {
        return installBundleInternal(location, null);
    }

    @Override
    public Bundle installBundle(String location, InputStream input) throws BundleException {
        return installBundleInternal(location, input);
    }

    /*
     * This is the entry point for all bundle deployments.
     *
     * #1 Construct the {@link VirtualFile} from the given parameters
     *
     * #2 Create a Bundle {@link Deployment}
     *
     * #3 Deploy the Bundle through the {@link BundleInstallProvider}
     *
     * The {@link BundleInstallProvider} is the integration point for JBossAS.
     *
     * The {@link DefaultBundleInstallProvider} simply delegates to {@link BundleManager#installBundle(ServiceTarget,Deployment)} In
     * JBossAS however, the {@link BundleInstallProvider} delegates to the management API that feeds the Bundle deployment
     * through the DeploymentUnitProcessor chain.
     */
    private Bundle installBundleInternal(String location, InputStream input) throws BundleException {
        checkValidBundleContext();

        Deployment dep;
        VirtualFile rootFile = null;
        FrameworkState frameworkState = getFrameworkState();
        try {
            if (input != null) {
                try {
                    rootFile = AbstractVFS.toVirtualFile(input);
                } catch (IOException ex) {
                    throw new BundleException("Cannot obtain virtual file from input stream", ex);
                }
            }

            // Try location as URL
            if (rootFile == null) {
                try {
                    URL url = new URL(location);
                    if (BundleProtocolHandler.PROTOCOL_NAME.equals(url.getProtocol())) {
                        rootFile = AbstractVFS.toVirtualFile(url.openStream());
                    } else {
                        try {
                            rootFile = AbstractVFS.toVirtualFile(url);
                        } catch (Exception ex) {
                            // This might possibly be a custom URL, try opening it the conventional way...
                            try {
                                InputStream is = url.openStream();
                                if (is != null) {
                                    // Note that this never recurses more than once
                                    return installBundleInternal(location, is);
                                }
                            } catch (IOException ioe) {
                                // ok that didn't work - ignore
                            }
                        }
                    }
                } catch (IOException ex) {
                    // Ignore, not a valid URL
                }
            }

            // Try location as File
            if (rootFile == null) {
                try {
                    File file = new File(location);
                    if (file.exists())
                        rootFile = AbstractVFS.toVirtualFile(file.toURI());
                } catch (IOException ex) {
                    throw new BundleException("Cannot obtain virtual file from: " + location, ex);
                }
            }

            if (rootFile == null)
                throw new BundleException("Cannot obtain virtual file from: " + location);

            DeploymentFactoryPlugin deploymentPlugin = frameworkState.getDeploymentFactoryPlugin();
            dep = deploymentPlugin.createDeployment(location, rootFile);

            return installBundle(dep);

        } catch (RuntimeException rte) {
            VFSUtils.safeClose(rootFile);
            throw rte;
        } catch (BundleException ex) {
            VFSUtils.safeClose(rootFile);
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    Bundle installBundle(Deployment dep) throws BundleException {
        checkValidBundleContext();

        FrameworkState frameworkState = getFrameworkState();
        BundleManager bundleManager = frameworkState.getBundleManager();
        ServiceTarget serviceTarget = bundleManager.getServiceTarget();

        //BundleService.addService(serviceTarget, dep);

        BundleInstallProvider installHandler = frameworkState.getCoreServices().getInstallHandler();
        installHandler.installBundle(serviceTarget, dep);

        ServiceName serviceName = dep.getAttachment(ServiceName.class);
        if (serviceName == null)
            throw new IllegalArgumentException("Cannot obtain service name for installed bundle: " + dep);

        ServiceContainer serviceContainer = bundleManager.getServiceContainer();
        ServiceController<UserBundleState> controller = (ServiceController<UserBundleState>) serviceContainer.getService(serviceName);
        FutureServiceValue<UserBundleState> future = new FutureServiceValue<UserBundleState>(controller);
        try {
            UserBundleState userBundle = future.get(5, TimeUnit.SECONDS);
            return userBundle;
        } catch (Exception ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof BundleException)
                throw (BundleException) cause;
            throw new BundleException("Cannot install bundle: " + dep.getLocation(), ex);
        }
    }

    @Override
    public Bundle getBundle(long id) {
        checkValidBundleContext();
        AbstractBundleState bundleState = getBundleManager().getBundleById(id);
        return (bundleState != null ? bundleState : null);
    }

    @Override
    public Bundle[] getBundles() {
        checkValidBundleContext();
        List<Bundle> result = new ArrayList<Bundle>();
        for (AbstractBundleState bundleState : getBundleManager().getBundles())
            result.add(bundleState);
        return result.toArray(new Bundle[result.size()]);
    }

    @Override
    public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException {
        checkValidBundleContext();
        getFrameworkEventsPlugin().addServiceListener(bundleState, listener, filter);
    }

    @Override
    public void addServiceListener(ServiceListener listener) {
        checkValidBundleContext();
        try {
            getFrameworkEventsPlugin().addServiceListener(bundleState, listener, null);
        } catch (InvalidSyntaxException ex) {
            // ignore
        }
    }

    @Override
    public void removeServiceListener(ServiceListener listener) {
        checkValidBundleContext();
        getFrameworkEventsPlugin().removeServiceListener(bundleState, listener);
    }

    @Override
    public void addBundleListener(BundleListener listener) {
        checkValidBundleContext();
        getFrameworkEventsPlugin().addBundleListener(bundleState, listener);
    }

    @Override
    public void removeBundleListener(BundleListener listener) {
        checkValidBundleContext();
        getFrameworkEventsPlugin().removeBundleListener(bundleState, listener);
    }

    @Override
    public void addFrameworkListener(FrameworkListener listener) {
        checkValidBundleContext();
        getFrameworkEventsPlugin().addFrameworkListener(bundleState, listener);
    }

    @Override
    public void removeFrameworkListener(FrameworkListener listener) {
        checkValidBundleContext();
        getFrameworkEventsPlugin().removeFrameworkListener(bundleState, listener);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ServiceRegistration registerService(String clazz, Object service, Dictionary properties) {
        checkValidBundleContext();
        return registerService(new String[] { clazz }, service, properties);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ServiceRegistration registerService(String[] clazzes, Object service, Dictionary properties) {
        checkValidBundleContext();
        ServiceManagerPlugin serviceManager = getFrameworkState().getServiceManagerPlugin();
        ServiceState serviceState = serviceManager.registerService(bundleState, clazzes, service, properties);
        return serviceState.getRegistration();
    }

    @Override
    public ServiceReference getServiceReference(String clazz) {
        checkValidBundleContext();
        ServiceManagerPlugin serviceManager = getFrameworkState().getServiceManagerPlugin();
        ServiceState serviceState = serviceManager.getServiceReference(bundleState, clazz);
        return (serviceState != null ? new ServiceReferenceWrapper(serviceState) : null);
    }

    @Override
    public ServiceReference[] getServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
        checkValidBundleContext();
        ServiceManagerPlugin serviceManager = getFrameworkState().getServiceManagerPlugin();
        List<ServiceState> srefs = serviceManager.getServiceReferences(bundleState, clazz, filter, true);
        if (srefs.isEmpty())
            return null;

        List<ServiceReference> result = new ArrayList<ServiceReference>();
        for (ServiceState serviceState : srefs)
            result.add(serviceState.getReference());

        return result.toArray(new ServiceReference[result.size()]);
    }

    @Override
    public ServiceReference[] getAllServiceReferences(String clazz, String filter) throws InvalidSyntaxException {
        checkValidBundleContext();
        ServiceManagerPlugin serviceManager = getFrameworkState().getServiceManagerPlugin();
        List<ServiceState> srefs = serviceManager.getServiceReferences(bundleState, clazz, filter, false);
        if (srefs.isEmpty())
            return null;

        List<ServiceReference> result = new ArrayList<ServiceReference>();
        for (ServiceState serviceState : srefs)
            result.add(serviceState.getReference());

        return result.toArray(new ServiceReference[result.size()]);
    }

    @Override
    public Object getService(ServiceReference sref) {
        checkValidBundleContext();
        ServiceState serviceState = ServiceState.assertServiceState(sref);
        ServiceManagerPlugin serviceManager = getFrameworkState().getServiceManagerPlugin();
        Object service = serviceManager.getService(bundleState, serviceState);
        return service;
    }

    @Override
    public boolean ungetService(ServiceReference sref) {
        checkValidBundleContext();
        ServiceState serviceState = ServiceState.assertServiceState(sref);
        return getServiceManager().ungetService(bundleState, serviceState);
    }

    @Override
    public File getDataFile(String filename) {
        checkValidBundleContext();
        BundleStoragePlugin storagePlugin = getFrameworkState().getBundleStoragePlugin();
        return storagePlugin.getDataFile(bundleState, filename);
    }

    @Override
    public Filter createFilter(String filter) throws InvalidSyntaxException {
        checkValidBundleContext();
        return FrameworkUtil.createFilter(filter);
    }

    void checkValidBundleContext() {
        if (destroyed == true)
            throw new IllegalStateException("Invalid bundle context: " + this);
    }

    private ServiceManagerPlugin getServiceManager() {
        return getFrameworkState().getServiceManagerPlugin();
    }

    private FrameworkEventsPlugin getFrameworkEventsPlugin() {
        return getFrameworkState().getFrameworkEventsPlugin();
    }

    @Override
    public String toString() {
        return "BundleContext[" + bundleState + "]";
    }
}
