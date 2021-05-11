# maven 命令解析

## mvn dependency:tree

1. mvn dependency:tree>temp/tree.txt
2. mvn dependency:tree -Dincludes=jline
3. mvn dependency:tree -Dverbose -Dincludes=commons-collections
4. mvn -DoutputDirectory=./lib 
         -DgroupId=com.it18zhang
         -DartifactId=CallLogConsumerModule 
         -Dversion=1.0-SNAPSHOT 
         dependency:copy-dependencies

## mvn 下载源码和 javadoc

1. mvn dependency:sources
2. mvn dependency:resolve -Dclassifier=javadoc

或者在 settings.xml 中配置

```xml
<profiles>
<profile>
    <id>downloadSources</id>
    <properties>
        <downloadSources>true</downloadSources>
        <downloadJavadocs>true</downloadJavadocs>           
    </properties>
</profile>
</profiles>
 
<activeProfiles>
  <activeProfile>downloadSources</activeProfile>
</activeProfiles>
```
