docx4j-xhtml2word
==================

Converts XHTML to OpenXML WordML (docx) using docx4j, There is also some support for converting to pptx.

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.docx4j/docx4j-ImportXHTML/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.docx4j/docx4j-ImportXHTML)

docx4j is licensed under ASLv2.

This project is licensed under LGPL v2.1 (or later), which is the license used by openhtmltopdf (the main dependency).  
See legals/NOTICE for details.


docx4j for JAXB 3.0 and Java 11+
--------------------------------

docx4j-ImportXHTML v11.4.6 uses Jakarta XML Binding API 3.0, as opposed to JAXB 2.x used in earlier versions (which import javax.xml.bind.*).  Since this release uses jakarta.xml.bind, rather than javax.xml.bind, if you have existing code which imports javax.xml.bind, you'll need to search/replace across your code base, replacing javax.xml.bind with jakarta.xml.bind. You'll also need to replace your JAXB jars (which Maven will do for you automatically; otherwise get them from the relevant zip file).

Being a JPMS modularised release, the jars also contain module-info.class entries.

To use it, add [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.docx4j/docx4j-ImportXHTML/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.docx4j/docx4j-ImportXHTML)


plus the dep corresponding to the JAXB implementation you wish to use

* [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.docx4j/docx4j-JAXB-ReferenceImpl/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.docx4j/docx4j-JAXB-ReferenceImpl)
 docx4j-JAXB-ReferenceImpl
* [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.docx4j/docx4j-JAXB-MOXy/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.docx4j/docx4j-JAXB-MOXy)
 docx4j-JAXB-MOXy

You should use one and only one of docx4j-JAXB-*
 
How do I build docx4j?
----------------------
```
mvn clean package
```

How to publish to central maven repository?
-----------------------
### Preparation
1) Install GPG and generate key
```shell
gpg --full-generate-key
```
2) Publish public key to GPG server
```shell
gpg --keyserver hkp://keyserver.ubuntu.com --send-keys <your generated public key>
```
3) Check if published successfully
```shell
gpg --keyserver hkp://keyserver.ubuntu.com --recv-keys <your generated public key>
```
### Run commands to deploy
```shell
mvn clean deploy
```