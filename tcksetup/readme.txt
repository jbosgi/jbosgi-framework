Setup the OSGi TCK
------------------

Checkout the OSGi TCK 

		git clone https://www.osgi.org/members/git/build org.osgi.build
		git checkout r5-core-ri-ct-final
	
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

