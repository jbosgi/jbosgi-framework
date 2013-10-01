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
package org.jboss.osgi.framework.internal;

import static org.jboss.osgi.framework.FrameworkMessages.MESSAGES;
import static org.osgi.framework.Constants.EXTENSION_BOOTCLASSPATH;
import static org.osgi.framework.Constants.EXTENSION_DIRECTIVE;
import static org.osgi.framework.Constants.EXTENSION_FRAMEWORK;
import static org.osgi.framework.Constants.PACKAGE_SPECIFICATION_VERSION;
import static org.osgi.framework.Constants.SYSTEM_BUNDLE_SYMBOLICNAME;
import static org.osgi.framework.Constants.VERSION_ATTRIBUTE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.PackageAttribute;
import org.jboss.osgi.metadata.ParameterizedAttribute;
import org.jboss.osgi.resolver.XBundleRevision;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

/**
 * A bundle validator for OSGi R4.
 *
 * @author thomas.diesler@jboss.com
 * @version $Revision: 1.1 $
 */
final class BundleRevisionValidatorR4 implements BundleRevisionValidator {

    @Override
    @SuppressWarnings("deprecation")
    public void validateBundleRevision(XBundleRevision brev, OSGiMetaData metadata) throws BundleException {

        // Missing Bundle-SymbolicName
        final String symbolicName = metadata.getBundleSymbolicName();
        if (symbolicName == null)
            throw MESSAGES.missingBundleSymbolicName(brev);

        // Bundle-ManifestVersion value not equal to 2, unless the Framework specifically
        // recognizes the semantics of a later release.
        int manifestVersion = metadata.getBundleManifestVersion();
        if (manifestVersion > 2)
            throw MESSAGES.unsupportedBundleManifestVersion(manifestVersion, brev);

        // Multiple imports of a given package.
        // Specification-version and version specified together (for the same package(s)) but with different values
        List<PackageAttribute> importPackages = metadata.getImportPackages();
        Set<String> packages = new HashSet<String>();
        for (PackageAttribute packageAttribute : importPackages) {
            String packageName = packageAttribute.getAttribute();
            if (packages.contains(packageName))
                throw MESSAGES.duplicatePackageImport(packageName, brev);
            packages.add(packageName);

            if (packageName.startsWith("java."))
                throw MESSAGES.notAllowdToImportJavaPackage(brev);

            String version = packageAttribute.getAttributeValue(VERSION_ATTRIBUTE, String.class);
            String specificationVersion = packageAttribute.getAttributeValue(PACKAGE_SPECIFICATION_VERSION, String.class);
            if (version != null && specificationVersion != null && version.equals(specificationVersion) == false)
                throw MESSAGES.packageVersionAndSpecificationVersionMissmatch(packageName, brev);
        }

        // Export or import of java.*.
        // Specification-version and version specified together (for the same package(s)) but with different values
        // The export statement must not specify an explicit bundle symbolic name nor bundle version
        List<PackageAttribute> exportPackages = metadata.getExportPackages();
        for (PackageAttribute packageAttr : exportPackages) {
            String packageName = packageAttr.getAttribute();
            if (packageName.startsWith("java."))
                throw MESSAGES.notAllowdToExportJavaPackage(brev);

            String versionAttr = packageAttr.getAttributeValue(Constants.VERSION_ATTRIBUTE, String.class);
            String specificationAttr = packageAttr.getAttributeValue(Constants.PACKAGE_SPECIFICATION_VERSION, String.class);
            if (versionAttr != null && specificationAttr != null && versionAttr.equals(specificationAttr) == false)
                throw MESSAGES.packageVersionAndSpecificationVersionMissmatch(packageName, brev);

            String symbolicNameAttr = packageAttr.getAttributeValue(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE, String.class);
            if (symbolicNameAttr != null)
                throw MESSAGES.packageCannotSpecifyBundleSymbolicName(packageName, brev);

            String bundleVersionAttr = packageAttr.getAttributeValue(Constants.BUNDLE_VERSION_ATTRIBUTE, String.class);
            if (bundleVersionAttr != null)
                throw MESSAGES.packageCannotSpecifyBundleVersion(packageName, brev);
        }

        // A bundle with a dynamic imported package having different values for version and specification-version attributes must fail to install
        List<PackageAttribute> dynamicImports = metadata.getDynamicImports();
        for (PackageAttribute packageAttr : dynamicImports) {
            String packageName = packageAttr.getAttribute();
            String versionAttr = packageAttr.getAttributeValue(Constants.VERSION_ATTRIBUTE, String.class);
            String specificationAttr = packageAttr.getAttributeValue(Constants.PACKAGE_SPECIFICATION_VERSION, String.class);
            if (versionAttr != null && specificationAttr != null && versionAttr.equals(specificationAttr) == false)
                throw MESSAGES.packageVersionAndSpecificationVersionMissmatch(packageName, brev);
        }

        // Verify Fragment-Host header
        if (brev.isFragment()) {
            ParameterizedAttribute hostAttr = metadata.getFragmentHost();
            String fragmentHost = hostAttr.getAttribute();
            String extension = hostAttr.getDirectiveValue(EXTENSION_DIRECTIVE, String.class);
            if (extension != null) {
                if (SYSTEM_BUNDLE_SYMBOLICNAME.equals(fragmentHost) == false)
                    throw MESSAGES.invalidFragmentHostForExtensionFragment(brev);

                if (EXTENSION_BOOTCLASSPATH.equals(extension))
                    throw MESSAGES.unsupportedBootClasspathExtension();

                if (EXTENSION_FRAMEWORK.equals(extension))
                    throw MESSAGES.unsupportedFrameworkExtension();
            }
        }

        // [TODO] Duplicate attribute or duplicate directive (except in the Bundle-Native code clause).

        // [TODO] Export-Package with a mandatory attribute that is not defined.

        // [TODO] Any syntactic error (for example, improperly formatted version or bundle symbolic name, unrecognized directive
        // value, etc.).

        // [TODO] The manifest lists a OSGI-INF/permission.perm file but no such file is present.

        // [TODO] Requiring the same bundle symbolic name more than once
    }
}
