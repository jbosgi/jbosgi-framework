/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.jboss.logging.Cause;
import org.jboss.logging.Message;
import org.jboss.logging.MessageBundle;
import org.jboss.logging.Messages;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceName;
import org.jboss.osgi.deployment.deployer.Deployment;
import org.jboss.osgi.deployment.interceptor.LifecycleInterceptorException;
import org.jboss.osgi.metadata.ParameterizedAttribute;
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

    @Message(id = 11208, value = "Cannot obtain service name for installed bundle: %s")
    IllegalStateException illegalStateCannotObtainPaths(Deployment deployment);

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

    //@Message(id = 11215, value = "Cannot install initial bundle: %s")
    //IllegalStateException illegalStateCannotInstallInitialBundle(@Cause Throwable cause, Object source);

    @Message(id = 11216, value = "Cannot create framework module")
    IllegalStateException illegalStateCannotCreateFrameworkModule(@Cause Throwable cause);

    @Message(id = 11217, value = "Module already exists: %s")
    IllegalStateException illegalStateModuleAlreadyExists(ModuleIdentifier identifier);

    @Message(id = 11218, value = "Cannot load module: %s")
    IllegalStateException illegalStateCannotLoadModule(@Cause Throwable cause, ModuleIdentifier identifier);

    @Message(id = 11219, value = "System paths provider not initialized")
    IllegalStateException illegalStateSystemPathsNotInitialized();

    @Message(id = 11220, value = "Cannot obtain attached host for: %s")
    IllegalStateException illegalStateCannotObtainAttachedHost(BundleRevision brev);

    @Message(id = 11221, value = "Framework builder already closed")
    IllegalStateException illegalStateFrameworkBuilderClosed();

    @Message(id = 11222, value = "Framework already stopped")
    IllegalStateException illegalStateFrameworkAlreadyStopped();

    @Message(id = 11223, value = "Framework not initialized")
    IllegalStateException illegalStateFrameworkNotInitialized();

    @Message(id = 11224, value = "Cannot find native library: %s")
    IllegalStateException illegalStateCannotFindNativeLibrary(String libpath);

    @Message(id = 11225, value = "Service unregistered: %s")
    IllegalStateException illegalStateServiceUnregistered(ServiceState serviceState);

    @Message(id = 11226, value = "Cannot create system bundle storage")
    IllegalStateException illegalStateCannotCreateSystemBundleStorage(@Cause Throwable cause);

    @Message(id = 11227, value = "No stream handlers for protocol: %s")
    IllegalStateException illegalStateNoStreamHandlersForProtocol(String protocol);

    @Message(id = 11228, value = "Attempt to refresh an unresolved bundle: %s")
    IllegalStateException illegalStateRefreshUnresolvedBundle(Bundle bundle);

    @Message(id = 11229, value = "Cannot obtain URL for: %s")
    IllegalStateException illegalStateCannotObtainURL(VirtualFile child);

    @Message(id = 11230, value = "Cannot obtain virtual file from input stream")
    BundleException bundleCannotObtainVirtualFile(@Cause Throwable cause);

    @Message(id = 11231, value = "Cannot obtain virtual file for: %s")
    BundleException bundleCannotObtainVirtualFileForLocation(@Cause Throwable cause, String location);

    @Message(id = 11232, value = "Cannot install bundle for: %s")
    BundleException bundleCannotInstallBundleForLocation(@Cause Throwable cause, String location);

    @Message(id = 11234, value = "Unsupported bundle manifest version %d in: %s")
    BundleException bundleUnsupportedBundleManifestVersion(int version, Bundle bundle);

    @Message(id = 11235, value = "Missing Bundle-SymbolicName in: %s")
    BundleException bundleMissingBundleSymbolicName(Bundle bundle);

    @Message(id = 11236, value = "Duplicate import of package '%s' in: %s")
    BundleException bundleDuplicatePackageImport(String packageName, Bundle bundle);

    @Message(id = 11237, value = "Not allowed to import java.* in: %s")
    BundleException bundleNotAllowdToImportJavaPackage(Bundle bundle);

    @Message(id = 11238, value = "Not allowed to export java.* in: %s")
    BundleException bundleNotAllowdToExportJavaPackage(Bundle bundle);

    @Message(id = 11239, value = "Version and specification version for package '%s' missmatch in: %s")
    BundleException bundlePackageVersionAndSpecificationVersionMissmatch(String packageName, Bundle bundle);

    @Message(id = 11240, value = "Package '%s' cannot specify explicit bundle-symbolicname in: %s")
    BundleException bundlePackageCannotSpecifyBundleSymbolicName(String packageName, Bundle bundle);

    @Message(id = 11241, value = "Package '%s' cannot specify explicit bundle-version in: %s")
    BundleException bundlePackageCannotSpecifyBundleVersion(String packageName, Bundle bundle);

    @Message(id = 11242, value = "Bundle name and version already installed: %s")
    BundleException bundleNameAndVersionAlreadyInstalled(Bundle bundle);

    @Message(id = 11243, value = "Invalid Fragment-Host for extension fragment: %s")
    BundleException bundleInvalidFragmentHostForExtensionFragment(Bundle bundle);

    @Message(id = 11244, value = "Invalid number format: %s")
    BundleException bundleInvalidNumberFormat(@Cause Throwable cause, String message);

    @Message(id = 11245, value = "Not a valid deployment: %s")
    BundleException bundleInvalidDeployment(Deployment deployment);

    @Message(id = 11246, value = "Fragments cannot be started")
    BundleException bundleCannotStartFragment();

    @Message(id = 11247, value = "Fragments cannot be stopped")
    BundleException bundleCannotStopFragment();

    @Message(id = 11248, value = "Cannot initialize Framework")
    BundleException bundleCannotInitializeFramework(@Cause Throwable cause);

    @Message(id = 11249, value = "Cannot start Framework")
    BundleException bundleCannotStartFramework(@Cause Throwable cause);

    @Message(id = 11250, value = "System bundle cannot be uninstalled")
    BundleException bundleCannotUninstallSystemBundle();

    @Message(id = 11251, value = "Bundle cannot be started due to current start level")
    BundleException bundleCannotStartBundleDueToStartLevel();

    @Message(id = 11252, value = "Cannot resolve bundle: %s")
    BundleException bundleCannotResolveBundle(@Cause Throwable cause, Bundle bundle);

    @Message(id = 11253, value = "Unsupported execution environment %s we have: %s")
    BundleException bundleUnsupportedExecutionEnvironment(List<String> required, List<String> available);

    @Message(id = 11254, value = "Cannot transition to STARTING: %s")
    BundleException bundleCannotTransitionToStarting(@Cause Throwable cause, Bundle bundle);

    @Message(id = 11255, value = "Invalid bundle activator: %s")
    BundleException bundleInvalidBundleActivator(String className);

    @Message(id = 11256, value = "Cannot start bundle: %s")
    BundleException bundleCannotStartBundle(@Cause Throwable cause, Bundle bundle);

    @Message(id = 11257, value = "Bundle was uninstalled during activator start: %s")
    BundleException bundleBundleUninstalledDuringActivatorStart(Bundle bundle);

    @Message(id = 11258, value = "Bundle was uninstalled during activator stop: %s")
    BundleException bundleBundleUninstalledDuringActivatorStop(Bundle bundle);

    @Message(id = 11259, value = "Error during activator stop: %s")
    BundleException bundleErrorDuringActivatorStop(@Cause Throwable cause, Bundle bundle);

    @Message(id = 11260, value = "Cannot acquire start/stop lock for: %s")
    BundleException bundleCannotAcquireStartStopLock(Bundle bundle);

    @Message(id = 11261, value = "Cannot find Bundle-NativeCode header for: %s")
    BundleException bundleCannotFindNativeCodeHeader(BundleRevision brev);

    @Message(id = 11262, value = "No native code clause selected for: %s")
    BundleException bundleNoNativeCodeClauseSelected(List<ParameterizedAttribute> params);

    @Message(id = 11263, value = "Invalid filter expression: %s")
    BundleException bundleInvalidFilterExpression(@Cause Throwable cause, String filterSpec);

    @Message(id = 11264, value = "Cannot install persisted bundles")
    BundleException bundleCannotInstallPersistedBundles(@Cause Throwable cause);

    @Message(id = 11265, value = "Cannot setup storage for: %s")
    BundleException bundleCannotSetupStorage(@Cause Throwable cause, VirtualFile virtualFile);

    @Message(id = 11266, value = "Cannot obtain revision content for: %s")
    IOException ioCannotObtainRevisionContent(URL url);

    @Message(id = 11267, value = "Cannot obtain content for: %s")
    IOException ioCannotObtainContent(URL url);

    @Message(id = 11268, value = "Cannot open connection on: %s")
    IOException ioCannotOpenConnectionOnHandler(@Cause Throwable cause, URLStreamHandlerService handler);

    @Message(id = 11269, value = "Cannot load class from fragment: %s")
    ClassNotFoundException cannotLoadClassFromFragment(BundleRevision brev);

    @Message(id = 11270, value = "Class '%s' not found in bundle revision: %s")
    ClassNotFoundException classNotFoundInRevision(String className, BundleRevision brev);

    @Message(id = 11271, value = "Cannot load class '%s' from bundle revision: %s")
    ClassNotFoundException cannotLoadClassFromBundleRevision(@Cause Throwable cause, String className, BundleRevision brev);

    @Message(id = 11272, value = "Cannot obtain web.xml from: %s")
    LifecycleInterceptorException lifecycleInterceptorCannotObtainWebXML(URL rootURL);

    @Message(id = 11273, value = "Cannot obtain web.xml")
    LifecycleInterceptorException lifecycleInterceptorCannotObtainWebXML(@Cause Throwable cause);

    @Message(id = 11274, value = "Timeout getting: %s")
    TimeoutException timeoutGettingService(String serviceName);

    @Message(id = 11275, value = "Cannot get service value for: %s")
    ExecutionException executionCannotGetServiceValue(@Cause Throwable cause, String serviceName);

    @Message(id = 11276, value = "Boot classpath extension not supported")
    UnsupportedOperationException unsupportedBootClasspathExtension();

    @Message(id = 11277, value = "Framework extension not supported")
    UnsupportedOperationException unsupportedFrameworkExtension();

    @Message(id = 11278, value = "Timeout waiting for bundle install service: %s")
    TimeoutException timeoutWaitingForBundleInstallService(Set<ServiceName> services);
}
