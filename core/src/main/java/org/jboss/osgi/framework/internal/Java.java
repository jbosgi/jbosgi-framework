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

/**
 * Provides common access to specifics about the version of <em>Java</em> that a virtual machine supports.
 *
 * <p>
 * Determines the version of the <em>Java Virtual Machine</em> by checking for the availablity of version specific classes.
 * <p>
 *
 * <p>
 * Classes are loaded in the following order:
 * <ol>
 * <li><tt>java.lang.Void</tt> was introduced in JDK 1.1</li>
 * <li><tt>java.lang.ThreadLocal</tt> was introduced in JDK 1.2</li>
 * <li><tt>java.lang.StrictMath</tt> was introduced in JDK 1.3</li>
 * <li><tt>java.lang.StackTraceElement</tt> was introduced in JDK 1.4</li>
 * <li><tt>java.lang.Enum</tt> was introduced in JDK 1.5</li>
 * <li><tt>java.lang.management.LockInfo</tt> was introduced in JDK 1.6</li>
 * <li><tt>java.nio.file</tt> was introduced in JDK 1.7</li>
 * </ol>
 * </p>
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @author <a href="mailto:dimitris@jboss.org">Dimitris Andreadis</a>
 * @author Thomas.Diesler@jboss.com
 */
public final class Java {

    /** Prevent instantiation */
    private Java() {
    }

    public static final int VERSION_1_0 = 0x01;
    public static final int VERSION_1_1 = 0x02;
    public static final int VERSION_1_2 = 0x03;
    public static final int VERSION_1_3 = 0x04;
    public static final int VERSION_1_4 = 0x05;
    public static final int VERSION_1_5 = 0x06;
    public static final int VERSION_1_6 = 0x07;
    public static final int VERSION_1_7 = 0x08;

    /**
     * Private to avoid over optimization by the compiler.
     *
     * @see #getVersion() Use this method to access this final value.
     */
    private static final int VERSION;

    /** Initialize VERSION. */
    static {
        // default to 1.0
        int version = VERSION_1_0;

        try {
            // check for 1.1
            Class.forName("java.lang.Void");
            version = VERSION_1_1;

            // check for 1.2
            Class.forName("java.lang.ThreadLocal");
            version = VERSION_1_2;

            // check for 1.3
            Class.forName("java.lang.StrictMath");
            version = VERSION_1_3;

            // check for 1.4
            Class.forName("java.lang.StackTraceElement");
            version = VERSION_1_4;

            // check for 1.5
            Class.forName("java.lang.Enum");
            version = VERSION_1_5;

            // check for 1.6
            Class.forName("java.lang.management.LockInfo");
            version = VERSION_1_6;

            // check for 1.7
            Class.forName("java.nio.file.FileStore");
            version = VERSION_1_7;
        } catch (ClassNotFoundException ignore) {
        }
        VERSION = version;
    }

    /**
     * Return the version of <em>Java</em> supported by the VM.
     *
     * @return The version of <em>Java</em> supported by the VM.
     */
    public static int getVersion() {
        return VERSION;
    }

    /**
     * Returns true if the given version identifier is equal to the version identifier of the current virtual machine.
     *
     * @param version The version identifier to check for.
     * @return True if the current virtual machine is the same version.
     */
    public static boolean isVersion(final int version) {
        return VERSION == version;
    }

    /**
     * Returns true if the current virtual machine is compatible with the given version identifier.
     *
     * @param version The version identifier to check compatibility of.
     * @return True if the current virtual machine is compatible.
     */
    public static boolean isCompatible(final int version) {
        // if our vm is the same or newer then we are compatible
        return VERSION >= version;
    }
}
