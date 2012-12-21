package org.jboss.osgi.framework;
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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.framework.spi.LockException;
import org.jboss.osgi.framework.spi.ServiceState;
import org.jboss.osgi.framework.spi.LockManager.LockContext;
import org.jboss.osgi.metadata.ParameterizedAttribute;
import org.jboss.osgi.resolver.XResource;
import org.jboss.osgi.vfs.VirtualFile;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.service.url.URLStreamHandlerService;

/**
 * Logging Id ranges: 11200-10399
 *
 * https://docs.jboss.org/author/display/JBOSGI/JBossOSGi+Logging
 *
 * @author Thomas.Diesler@jboss.com
 */
@MessageBundle(projectCode = "JBOSGI")
public interface FrameworkMessages {

    FrameworkMessages MESSAGES = Messages.getBundle(FrameworkMessages.class);

    @Message(id = 11200, value = "%s is null")
    IllegalArgumentException illegalArgumentNull(String name);

    @Message(id = 11201, value = "Unknown bundle state: %d")
    IllegalArgumentException illegalArgumentUnknownBundleState(int state);

    @Message(id = 11202, value = "Required property '%s' missing in: %s")
    IllegalArgumentException illegalArgumentRequiredPropertyMissing(String key, File file);

    @Message(id = 11203, value = "Invalid path: %s")
    IllegalArgumentException illegalArgumentInvalidPath(@Cause Throwable cause, String path);

    @Message(id = 11204, value = "Invalid object class in: %s")
    IllegalArgumentException illegalArgumentInvalidObjectClass(String classNames);

    @Message(id = 11205, value = "Invalid service reference: %s")
    IllegalArgumentException illegalArgumentInvalidServiceRef(Object sref);

    @Message(id = 11206, value = "Cannot set the start level on system bundle")
    IllegalArgumentException illegalArgumentStartLevelOnSystemBundles();

    @Message(id = 11207, value = "Cannot obtain paths from: %s")
    IllegalArgumentException illegalArgumentCannotObtainPaths(@Cause Throwable cause, VirtualFile virtualFile);

    //@Message(id = 11208, value = "Cannot obtain bundle INSTALLED service: %s")
    //IllegalStateException illegalStateCannotObtainBundleInstalledService(Deployment deployment);

    @Message(id = 11209, value = "Invalid bundle context for: %s")
    IllegalStateException illegalStateInvalidBundleContext(Bundle bundle);

    @Message(id = 11210, value = "Bundle already uninstalled: %s")
    IllegalStateException illegalStateBundleAlreadyUninstalled(Bundle bundle);

    @Message(id = 11211, value = "Cannot read resouce bundle: %s")
    IllegalStateException illegalStateCannotReadResourceBundle(@Cause Throwable cause, URL entryURL);

    @Message(id = 11212, value = "Framework not ACTIVE")
    IllegalStateException illegalStateFrameworkNotActive();

    @Message(id = 11213, value = "Cannot add property to ACTIVE framework")
    IllegalStateException illegalStateCannotAddProperty();

    @Message(id = 11214, value = "Cannot create storage area")
    IllegalStateException illegalStateCannotCreateStorageArea(@Cause Throwable cause);

    @Message(id = 11215, value = "Cannot create framework module")
    IllegalStateException illegalStateCannotCreateFrameworkModule(@Cause Throwable cause);

    @Message(id = 11216, value = "Module already exists: %s")
    IllegalStateException illegalStateModuleAlreadyExists(ModuleIdentifier identifier);

    @Message(id = 11217, value = "Cannot load module: %s")
    IllegalStateException illegalStateCannotLoadModule(@Cause Throwable cause, ModuleIdentifier identifier);

    @Message(id = 11218, value = "System paths provider not initialized")
    IllegalStateException illegalStateSystemPathsNotInitialized();

    @Message(id = 11219, value = "Cannot obtain attached host for: %s")
    IllegalStateException illegalStateCannotObtainAttachedHost(BundleRevision brev);

    @Message(id = 11220, value = "Framework builder already closed")
    IllegalStateException illegalStateFrameworkBuilderClosed();

    @Message(id = 11221, value = "Framework already stopped")
    IllegalStateException illegalStateFrameworkAlreadyStopped();

    @Message(id = 11222, value = "Framework not initialized")
    IllegalStateException illegalStateFrameworkNotInitialized();

    @Message(id = 11223, value = "Cannot find native library: %s")
    IllegalStateException illegalStateCannotFindNativeLibrary(String libpath);

    @Message(id = 11224, value = "Service unregistered: %s")
    IllegalStateException illegalStateServiceUnregistered(ServiceState serviceState);

    @Message(id = 11225, value = "Cannot create system bundle storage")
    IllegalStateException illegalStateCannotCreateSystemBundleStorage(@Cause Throwable cause);

    @Message(id = 11226, value = "No stream handlers for protocol: %s")
    IllegalStateException illegalStateNoStreamHandlersForProtocol(String protocol);

    @Message(id = 11227, value = "Attempt to refresh an unresolved bundle: %s")
    IllegalStateException illegalStateRefreshUnresolvedBundle(Bundle bundle);

    @Message(id = 11228, value = "Cannot obtain URL for: %s")
    IllegalStateException illegalStateCannotObtainURL(VirtualFile child);

    @Message(id = 11229, value = "Cannot obtain virtual file from input stream")
    BundleException cannotObtainVirtualFile(@Cause Throwable cause);

    @Message(id = 11230, value = "Cannot obtain virtual file for: %s")
    BundleException cannotObtainVirtualFileForLocation(@Cause Throwable cause, String location);

    @Message(id = 11231, value = "Cannot install bundle from: %s")
    BundleException cannotInstallBundleFromDeployment(@Cause Throwable cause, Deployment dep);

    @Message(id = 11232, value = "Unsupported bundle manifest version %d in: %s")
    BundleException unsupportedBundleManifestVersion(int version, Bundle bundle);

    @Message(id = 11233, value = "Missing Bundle-SymbolicName in: %s")
    BundleException missingBundleSymbolicName(Bundle bundle);

    @Message(id = 11234, value = "Duplicate import of package '%s' in: %s")
    BundleException duplicatePackageImport(String packageName, Bundle bundle);

    @Message(id = 11235, value = "Not allowed to import java.* in: %s")
    BundleException notAllowdToImportJavaPackage(Bundle bundle);

    @Message(id = 11236, value = "Not allowed to export java.* in: %s")
    BundleException notAllowdToExportJavaPackage(Bundle bundle);

    @Message(id = 11237, value = "Version and specification version for package '%s' missmatch in: %s")
    BundleException packageVersionAndSpecificationVersionMissmatch(String packageName, Bundle bundle);

    @Message(id = 11238, value = "Package '%s' cannot specify explicit bundle-symbolicname in: %s")
    BundleException packageCannotSpecifyBundleSymbolicName(String packageName, Bundle bundle);

    @Message(id = 11239, value = "Package '%s' cannot specify explicit bundle-version in: %s")
    BundleException packageCannotSpecifyBundleVersion(String packageName, Bundle bundle);

    @Message(id = 11240, value = "Bundle name and version already installed: %s")
    BundleException nameAndVersionAlreadyInstalled(Bundle bundle);

    @Message(id = 11241, value = "Invalid Fragment-Host for extension fragment: %s")
    BundleException invalidFragmentHostForExtensionFragment(Bundle bundle);

    @Message(id = 11242, value = "Invalid number format: %s")
    BundleException invalidNumberFormat(@Cause Throwable cause, String message);

    @Message(id = 11243, value = "Not a valid deployment: %s")
    BundleException invalidDeployment(Deployment deployment);

    @Message(id = 11244, value = "Fragments cannot be started")
    BundleException cannotStartFragment();

    @Message(id = 11245, value = "Fragments cannot be stopped")
    BundleException cannotStopFragment();

    @Message(id = 11246, value = "Cannot initialize Framework")
    BundleException cannotInitializeFramework(@Cause Throwable cause);

    @Message(id = 11247, value = "Cannot start Framework")
    BundleException cannotStartFramework(@Cause Throwable cause);

    @Message(id = 11248, value = "System bundle cannot be uninstalled")
    BundleException cannotUninstallSystemBundle();

    @Message(id = 11249, value = "Bundle cannot be started due to current start level")
    BundleException cannotStartBundleDueToStartLevel();

    @Message(id = 11250, value = "Cannot resolve bundle: %s")
    BundleException cannotResolveBundle(@Cause Throwable cause, Bundle bundle);

    @Message(id = 11251, value = "Unsupported execution environment %s we have: %s")
    BundleException unsupportedExecutionEnvironment(List<String> required, List<String> available);

    @Message(id = 11252, value = "Cannot transition to STARTING: %s")
    BundleException cannotTransitionToStarting(@Cause Throwable cause, Bundle bundle);

    @Message(id = 11253, value = "Invalid bundle activator: %s")
    BundleException invalidBundleActivator(String className);

    @Message(id = 11254, value = "Cannot start bundle: %s")
    BundleException cannotStartBundle(@Cause Throwable cause, Bundle bundle);

    @Message(id = 11255, value = "Bundle was uninstalled during activator start: %s")
    BundleException uninstalledDuringActivatorStart(Bundle bundle);

    @Message(id = 11256, value = "Bundle was uninstalled during activator stop: %s")
    BundleException uninstalledDuringActivatorStop(Bundle bundle);

    @Message(id = 11257, value = "Cannot stop bundle: %s")
    BundleException cannotStopBundle(@Cause Throwable cause, Bundle bundle);

    //@Message(id = 11258, value = "Cannot acquire start/stop lock for: %s")
    //BundleException cannotAcquireStartStopLock(Bundle bundle);

    @Message(id = 11259, value = "Cannot find Bundle-NativeCode header for: %s")
    BundleException cannotFindNativeCodeHeader(BundleRevision brev);

    @Message(id = 11260, value = "No native code clause selected for: %s")
    BundleException noNativeCodeClauseSelected(List<ParameterizedAttribute> params);

    @Message(id = 11261, value = "Invalid filter expression: %s")
    BundleException invalidFilterExpression(@Cause Throwable cause, String filterSpec);

    @Message(id = 11262, value = "Cannot install persisted bundles")
    BundleException cannotInstallPersistedBundles(@Cause Throwable cause);

    @Message(id = 11263, value = "Cannot setup storage for: %s")
    BundleException cannotSetupStorage(@Cause Throwable cause, VirtualFile virtualFile);

    @Message(id = 11264, value = "Cannot obtain revision content for: %s")
    IOException cannotObtainRevisionContent(URL url);

    @Message(id = 11265, value = "Cannot obtain content for: %s")
    IOException cannotObtainContent(URL url);

    @Message(id = 11266, value = "Cannot open connection on: %s")
    IOException cannotOpenConnectionOnHandler(@Cause Throwable cause, URLStreamHandlerService handler);

    @Message(id = 11267, value = "Cannot load class from fragment: %s")
    ClassNotFoundException cannotLoadClassFromFragment(BundleRevision brev);

    @Message(id = 11268, value = "Class '%s' not found in bundle revision: %s")
    ClassNotFoundException classNotFoundInRevision(String className, BundleRevision brev);

    //@Message(id = 11269, value = "Cannot load class '%s' from bundle revision: %s")
    //ClassNotFoundException cannotLoadClassFromBundleRevision(@Cause Throwable cause, String className, BundleRevision brev);

    //@Message(id = 11270, value = "Cannot obtain web.xml from: %s")
    //LifecycleInterceptorException cannotObtainWebXML(URL rootURL);

    //@Message(id = 11271, value = "Cannot obtain web.xml")
    //LifecycleInterceptorException cannotObtainWebXML(@Cause Throwable cause);

    @Message(id = 11272, value = "Timeout getting: %s")
    TimeoutException timeoutGettingService(String serviceName);

    @Message(id = 11273, value = "Cannot get service value for: %s")
    ExecutionException cannotGetServiceValue(@Cause Throwable cause, String serviceName);

    @Message(id = 11274, value = "Boot classpath extension not supported")
    UnsupportedOperationException unsupportedBootClasspathExtension();

    @Message(id = 11275, value = "Framework extension not supported")
    UnsupportedOperationException unsupportedFrameworkExtension();

    @Message(id = 11276, value = "Timeout waiting for bundle install service: %s")
    TimeoutException timeoutWaitingForBundleInstallService(Set<ServiceName> services);

    @Message(id = 11277, value = "Unsupported resource type: %s")
    IllegalArgumentException unsupportedResourceType(XResource res);

    @Message(id = 11278, value = "Cannot obtain bundle from resource: %s")
    IllegalArgumentException cannotObtainBundleFromResource(XResource res);

    @Message(id = 11279, value = "Unsupported operation on bundle: %s")
    BundleException unsupportedBundleOpertaion(Bundle bundle);

    //@Message(id = 11280, value = "Attempt to expand current lock context by: %s")
    //IllegalStateException cannotExpandCurrentLock(LockContext context);

    //@Message(id = 11281, value = "Cannot call out to client code while holding a lock: %s")
    //IllegalStateException currentThreadIsHoldingLock(LockContext context);

    @Message(id = 11282, value = "Cannot obtain lock in timely fashion: %s")
    LockException cannotObtainLockTimely(@Cause Throwable cause, LockContext context);

    @Message(id = 11283, value = "Revision content already closed: %s - [%d]")
    IllegalStateException illegalStateRevisionContentClosed(BundleRevision brev, int contentId);
}
