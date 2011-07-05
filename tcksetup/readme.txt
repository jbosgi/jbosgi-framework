Setup the OSGi TCK
------------------

Checkout the OSGi TCK 

		svn co https://www.osgi.org/members/svn/build/tags/r4v42-core-cmpn-final
	or
		svn co https://svn.devel.redhat.com/repos/jboss-tck/osgitck/r4v42
	
Copy and edit the setup properties

    cp ant.properties.example ant.properties
    vi ant.properties

Running the OSGi TCK against the RI (Equinox)

    ant clean setup.ri
    ant run-core-tests
    ant test-reports

Running the OSGi TCK against the JBoss OSGi Framework
    (This only works after having run 'ant clean setup.ri' at least once)

    ant clean setup.vi
    ant run-core-tests
    ant test-reports

Running Tests with JPDA
-----------------------

export ANT_OPTS="-Djpda=-Xrunjdwp:transport=dt_socket,address=8787,server=y,suspend=y"
