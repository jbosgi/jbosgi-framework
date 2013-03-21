package org.jboss.osgi.framework.internal;

/*
 * #%L
 * JBossOSGi Framework
 * %%
 * Copyright (C) 2010 - 2012 JBoss by Red Hat
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import static org.jboss.osgi.framework.FrameworkLogger.LOGGER;
import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.spi.BundleLifecycle;
import org.jboss.osgi.framework.spi.BundleManager;
import org.jboss.osgi.framework.spi.BundleStorage;
import org.jboss.osgi.framework.spi.DeploymentProvider;
import org.jboss.osgi.framework.spi.FrameworkEvents;
import org.jboss.osgi.framework.spi.FutureServiceValue;
import org.jboss.osgi.framework.spi.ServiceManager;
import org.jboss.osgi.framework.spi.ServiceState;
import org.jboss.osgi.resolver.spi.RemoveOnlyCollection;
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
import org.osgi.framework.hooks.bundle.FindHook;

/**
 * The base of all {@link BundleContext} implementations.
 *
 * @author thomas.diesler@jboss.com
 * @since 29-Jun-2010
 */
abstract class AbstractBundleContext<T extends AbstractBundleState<?>> implements BundleContext {

    private final T bundleState;
    private final FrameworkState frameworkState;
    private final BundleManager bundleManager;
    private boolean destroyed;

    AbstractBundleContext(T bundleState) {
        assert bundleState != null : "Null bundleState";
        this.bundleState = bundleState;
        this.frameworkState = bundleState.getFrameworkState();
        this.bundleManager = bundleState.getBundleManager();
    }

    /**
     * Assert that the given context is an instance of AbstractBundleContext
     */
    static AbstractBundleContext<?> assertBundleContext(BundleContext context) {
        assert context != null : "Null context";
        assert context instanceof AbstractBundleContext : "Not an AbstractBundleContext: " + context;
        return (AbstractBundleContext<?>) context;
    }

    void destroy() {
        destroyed = true;
    }

    T getBundleState() {
        return bundleState;
    }

    BundleManager getBundleManager() {
        return bundleManager;
    }

    BundleManagerPlugin getBundleManagerPlugin() {
        return BundleManagerPlugin.assertBundleManagerPlugin(bundleManager);
    }

    FrameworkState getFrameworkState() {
        return frameworkState;
    }

    @Override
    public String getProperty(String key) {
        checkValidBundleContext();
        getBundleManagerPlugin().assertFrameworkCreated();
        Object value = getBundleManager().getProperty(key);
        return (value instanceof String ? (String) value : null);
    }

    @Override
    public Bundle getBundle() {
        checkValidBundleContext();
        return bundleState;
    }

    @Override
    public Bundle getBundle(String location) {
        checkValidBundleContext();
        return getBundleManager().getBundleByLocation(location);
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
     * #3 Deploy the Bundle through the {@link BundleInstallPlugin}
     *
     * The {@link BundleInstallPlugin} is the integration point for JBossAS.
     *
     * The {@link DefaultBundleInstallPlugin} simply delegates to {@link BundleManager#installBundle(ServiceTarget,Deployment)} In
     * JBossAS however, the {@link BundleInstallPlugin} delegates to the management API that feeds the Bundle deployment
     * through the DeploymentUnitProcessor chain.
     */
    private Bundle installBundleInternal(String location, InputStream input) throws BundleException {
        checkValidBundleContext();

        // When an existing bundle is already installed at a given location, the find method is called to determine if the
        // context performing the install operation is able to find the bundle. If the context cannot find the existing bundle
        // then the install operation must fail with a BundleException.REJECTED_BY_HOOK exception
        Bundle bundle = getBundle(location);
        if (bundle != null) {
            Collection<Bundle> hookBundles = new RemoveOnlyCollection<Bundle>(bundle);
            callFindHooks(hookBundles);
            if (hookBundles.isEmpty()) {
                String message = MESSAGES.cannotFindLocationBundleInContext(location, this);
                throw new BundleException(message, BundleException.REJECTED_BY_HOOK);
            }
        }

        Deployment dep;
        VirtualFile rootFile = null;
        FrameworkState frameworkState = getFrameworkState();
        try {
            if (input != null) {
                try {
                    rootFile = AbstractVFS.toVirtualFile(input);
                } catch (IOException ex) {
                    throw MESSAGES.cannotObtainVirtualFile(ex);
                }
            }

            // Try location as URL
            if (rootFile == null) {
                try {
                    URL url = new URL(location);
                    rootFile = AbstractVFS.toVirtualFile(url.openStream());
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
                    throw MESSAGES.cannotObtainVirtualFileForLocation(ex, location);
                }
            }

            if (rootFile == null)
                throw MESSAGES.cannotObtainVirtualFileForLocation(null, location);

            DeploymentProvider deploymentPlugin = frameworkState.getDeploymentProvider();
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
        CoreServices coreServices = frameworkState.getCoreServices();
        try {
            BundleLifecycle bundleLifecycle = coreServices.getBundleLifecycle();
            bundleLifecycle.install(this, dep);
        } catch (BundleException ex) {
            LOGGER.debugf(ex, "Cannot install bundle from deployment: %s", dep);
            throw ex;
        }

        ServiceName serviceName = dep.getAttachment(ServiceName.class);
        assert serviceName != null : "Service name not attached to Deployment";

        ServiceContainer serviceContainer = bundleManager.getServiceContainer();
        ServiceController<Bundle> controller = (ServiceController<Bundle>) serviceContainer.getService(serviceName);
        FutureServiceValue<Bundle> future = new FutureServiceValue<Bundle>(controller);
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof BundleException) {
                throw (BundleException) cause;
            }
            throw MESSAGES.cannotInstallBundleFromDeployment(ex, dep);
        }
    }

    @Override
    public Bundle getBundle(long id) {
        checkValidBundleContext();
        Bundle result = getBundleManager().getBundleById(id);

        // Call the {@link FindHook}
        Collection<Bundle> hookBundles = new RemoveOnlyCollection<Bundle>(result);
        callFindHooks(hookBundles);

        return hookBundles.isEmpty() ? null : result;
    }

    @Override
    public Bundle[] getBundles() {
        checkValidBundleContext();
        List<Bundle> result = new ArrayList<Bundle>();
        for (Bundle bundle : getBundleManager().getBundles())
            result.add(bundle);

        // Call the {@link FindHook}
        Collection<Bundle> hookBundles = new RemoveOnlyCollection<Bundle>(result);
        callFindHooks(hookBundles);

        return result.toArray(new Bundle[hookBundles.size()]);
    }

    private void callFindHooks(Collection<Bundle> bundles) {
        Collection<ServiceReference<FindHook>> srefs = null;
        try {
            srefs = getServiceReferences(FindHook.class, null);
        } catch (InvalidSyntaxException ex) {
            // ignore
        }
        if (srefs != null && !srefs.isEmpty()) {
            // Hooks are always called in service ranking order
            List<ServiceReference<FindHook>> sortedRefs = new ArrayList<ServiceReference<FindHook>>(srefs);
            Collections.reverse(sortedRefs);
            for (ServiceReference<FindHook> sref : sortedRefs) {
                FindHook hook = getService(sref);
                try {
                    hook.find(this, bundles);
                } catch (Exception ex) {
                    LOGGER.warnErrorWhileCallingBundleFindHook(ex, hook);
                }
            }
        }
    }

    @Override
    public void addServiceListener(ServiceListener listener, String filter) throws InvalidSyntaxException {
        checkValidBundleContext();
        if (listener == null)
            throw MESSAGES.illegalArgumentNull("listener");
        getFrameworkEventsPlugin().addServiceListener(bundleState, listener, filter);
    }

    @Override
    public void addServiceListener(ServiceListener listener) {
        checkValidBundleContext();
        if (listener == null)
            throw MESSAGES.illegalArgumentNull("listener");
        try {
            getFrameworkEventsPlugin().addServiceListener(bundleState, listener, null);
        } catch (InvalidSyntaxException ex) {
            // ignore
        }
    }

    @Override
    public void removeServiceListener(ServiceListener listener) {
        checkValidBundleContext();
        if (listener == null)
            throw MESSAGES.illegalArgumentNull("listener");
        getFrameworkEventsPlugin().removeServiceListener(bundleState, listener);
    }

    @Override
    public void addBundleListener(BundleListener listener) {
        checkValidBundleContext();
        if (listener == null)
            throw MESSAGES.illegalArgumentNull("listener");
        getFrameworkEventsPlugin().addBundleListener(bundleState, listener);
    }

    @Override
    public void removeBundleListener(BundleListener listener) {
        checkValidBundleContext();
        if (listener == null)
            throw MESSAGES.illegalArgumentNull("listener");
        getFrameworkEventsPlugin().removeBundleListener(bundleState, listener);
    }

    @Override
    public void addFrameworkListener(FrameworkListener listener) {
        checkValidBundleContext();
        if (listener == null)
            throw MESSAGES.illegalArgumentNull("listener");
        getFrameworkEventsPlugin().addFrameworkListener(bundleState, listener);
    }

    @Override
    public void removeFrameworkListener(FrameworkListener listener) {
        checkValidBundleContext();
        if (listener == null)
            throw MESSAGES.illegalArgumentNull("listener");
        getFrameworkEventsPlugin().removeFrameworkListener(bundleState, listener);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ServiceRegistration<?> registerService(String clazz, Object service, Dictionary properties) {
        checkValidBundleContext();
        return registerService(new String[] { clazz }, service, properties);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ServiceRegistration<?> registerService(String[] classNames, Object service, Dictionary properties) {
        if (classNames == null || classNames.length == 0)
            throw MESSAGES.illegalArgumentNull("classNames");
        if (service == null)
            throw MESSAGES.illegalArgumentNull("service");
        checkValidBundleContext();
        ServiceManager serviceManager = getFrameworkState().getServiceManagerPlugin();
        ServiceState serviceState = serviceManager.registerService(bundleState, classNames, service, properties);
        return serviceState.getRegistration();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> ServiceRegistration<S> registerService(Class<S> clazz, S service, Dictionary<String, ?> properties) {
        if (clazz == null)
            throw MESSAGES.illegalArgumentNull("class");
        if (service == null)
            throw MESSAGES.illegalArgumentNull("service");
        checkValidBundleContext();
        String[] classNames = new String[] { clazz.getName() };
        ServiceManager serviceManager = getFrameworkState().getServiceManagerPlugin();
        ServiceState<S> serviceState = serviceManager.registerService(bundleState, classNames, service, properties);
        return serviceState.getRegistration();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ServiceReference<?> getServiceReference(String className) {
        if (className == null)
            throw MESSAGES.illegalArgumentNull("className");
        checkValidBundleContext();
        ServiceManager serviceManager = getFrameworkState().getServiceManagerPlugin();
        ServiceState<?> serviceState = serviceManager.getServiceReference(bundleState, className);
        return (serviceState != null ? new ServiceReferenceWrapper(serviceState) : null);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
        if (clazz == null)
            throw MESSAGES.illegalArgumentNull("className");
        checkValidBundleContext();
        ServiceManager serviceManager = getFrameworkState().getServiceManagerPlugin();
        ServiceState<?> serviceState = serviceManager.getServiceReference(bundleState, clazz.getName());
        return (serviceState != null ? new ServiceReferenceWrapper(serviceState) : null);
    }

    @Override
    public ServiceReference<?>[] getServiceReferences(String className, String filter) throws InvalidSyntaxException {
        checkValidBundleContext();
        ServiceManager serviceManager = getFrameworkState().getServiceManagerPlugin();
        List<ServiceState<?>> srefs = serviceManager.getServiceReferences(bundleState, className, filter, true);
        if (srefs.isEmpty())
            return null;

        List<ServiceReference<?>> result = new ArrayList<ServiceReference<?>>();
        for (ServiceState<?> serviceState : srefs)
            result.add(serviceState.getReference());

        return result.toArray(new ServiceReference[result.size()]);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter) throws InvalidSyntaxException {
        checkValidBundleContext();
        String className = clazz != null ? clazz.getName() : null;
        ServiceManager serviceManager = getFrameworkState().getServiceManagerPlugin();
        List<ServiceState<?>> srefs = serviceManager.getServiceReferences(bundleState, className, filter, true);

        List<ServiceReference<S>> result = new ArrayList<ServiceReference<S>>();
        for (ServiceState<?> serviceState : srefs)
            result.add((ServiceReference<S>) serviceState.getReference());

        return Collections.unmodifiableList(result);
    }

    @Override
    public ServiceReference<?>[] getAllServiceReferences(String className, String filter) throws InvalidSyntaxException {
        checkValidBundleContext();
        ServiceManager serviceManager = getFrameworkState().getServiceManagerPlugin();
        List<ServiceState<?>> srefs = serviceManager.getServiceReferences(bundleState, className, filter, false);
        if (srefs.isEmpty())
            return null;

        List<ServiceReference<?>> result = new ArrayList<ServiceReference<?>>();
        for (ServiceState<?> serviceState : srefs)
            result.add(serviceState.getReference());

        return result.toArray(new ServiceReference[result.size()]);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> S getService(ServiceReference<S> sref) {
        if (sref == null)
            throw MESSAGES.illegalArgumentNull("sref");
        checkValidBundleContext();
        ServiceState<?> serviceState = ServiceStateImpl.assertServiceState(sref);
        ServiceManager serviceManager = getFrameworkState().getServiceManagerPlugin();
        S service = (S) serviceManager.getService(bundleState, serviceState);
        return service;
    }

    @Override
    public boolean ungetService(ServiceReference<?> sref) {
        if (sref == null)
            throw MESSAGES.illegalArgumentNull("sref");
        checkValidBundleContext();
        ServiceState<?> serviceState = ServiceStateImpl.assertServiceState(sref);
        return getServiceManager().ungetService(bundleState, serviceState);
    }

    @Override
    public File getDataFile(String filename) {
        checkValidBundleContext();
        BundleStorage storagePlugin = getFrameworkState().getBundleStorage();
        return storagePlugin.getDataFile(bundleState.getBundleId(), filename);
    }

    @Override
    public Filter createFilter(String filter) throws InvalidSyntaxException {
        checkValidBundleContext();
        return FrameworkUtil.createFilter(filter);
    }

    void checkValidBundleContext() {
        if (destroyed == true)
            throw MESSAGES.illegalStateInvalidBundleContext(bundleState);
    }

    private ServiceManager getServiceManager() {
        return getFrameworkState().getServiceManagerPlugin();
    }

    private FrameworkEvents getFrameworkEventsPlugin() {
        return getFrameworkState().getFrameworkEvents();
    }

    @Override
    public String toString() {
        return "BundleContext[" + bundleState + "]";
    }
}
