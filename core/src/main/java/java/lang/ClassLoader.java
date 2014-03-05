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
package java.lang;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

/**
 * Compatibility class for the sake of JDK6 compilation.
 * It is stubbed out (excluded) from the result jar in pom.xml.
 *
 * {@link org.jboss.osgi.framework.spi.BundleReferenceClassLoader} calls a method {@code ClassLoader.registerAsParallelCapable()}
 * in its static initialization block which is available in JDK7 only.
 *
 * @author <a href="mailto:istudens@redhat.com">Ivo Studensky</a>
 */
public abstract class ClassLoader {
    public native Class<?> loadClass(String name) throws ClassNotFoundException;
    public native URL getResource(String name);
    public native Enumeration<URL> getResources(String name) throws IOException;
    public static native URL getSystemResource(String name);
    public static native Enumeration<URL> getSystemResources(String name);
    public native InputStream getResourceAsStream(String name);
    public static native InputStream getSystemResourceAsStream(String name);
    public final native ClassLoader getParent();
    public static native ClassLoader getSystemClassLoader();
    public synchronized native void setDefaultAssertionStatus(boolean enabled);
    public synchronized native void clearAssertionStatus();

    protected ClassLoader(ClassLoader parent) {}
    protected ClassLoader() {}
    protected native Class<?> findClass(String name) throws ClassNotFoundException;
    protected final native Class<?> defineClass(byte[] b, int off, int len);
    protected final native Class<?> defineClass(String name, byte[] b, int off, int len);
    protected final native void resolveClass(Class<?> c);
    protected final native Class<?> findSystemClass(String name);
    protected final native Class<?> findLoadedClass(String name);
    protected final native void setSigners(Class<?> c, Object[] signers);
    protected native URL findResource(String name);
    protected native Enumeration<URL> findResources(String name) throws IOException;
    protected native Package getPackage(String name);
    protected native Package[] getPackages();
    protected native String findLibrary(String libname);
    protected native void finalize();

    // JDK7 methods
    protected native Object getClassLoadingLock(String className);
    protected static native boolean registerAsParallelCapable();
}
