# i2b2-pm-cas
This is a stock i2b2 1.7.05 project management module patched with support for delegating authentication to Eureka via its patched [JASIG CAS](http://jasig.github.io/cas/4.1.x/index.html) server module, found at https://github.com/eurekaclinical/cas. JASIG CAS is a single sign-on system. This allows users of Eureka and i2b2 to login once and have access to both systems.

The code is adapted from similar code for an older version of i2b2 by Dan Connolly found at https://bitbucket.org/DanC/i2b2-pm-cas.

## Versions of CAS Supported

Eureka! currently uses a patched copy of CAS version 3.5.2. While these i2b2 patches have only been tested with Eureka!'s patched CAS, they likely also will work with stock CAS 3.5.2.

##Installation

Replace the stock project management module with this one before compiling i2b2, and it will be built and install as usual.

If you are installing i2b2 on a separate machine, create a file, `/etc/eureka/application.properties` with one line: 
`cas.url=URL to your cas server`. This file already exists if you have already installed Eureka on the same machine.

Finally, you need to install the SSL certificate served by CAS server into the cacerts file of the Java installation that i2b2 is using.

In order to login to i2b2 using CAS, you also need to install our patched i2b2 webclient, found at https://github.com/eurekaclinical/i2b2-webclient-cas.
