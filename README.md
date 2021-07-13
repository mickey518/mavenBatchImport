# 程序批量上传 jar

    上传目录不能和本地的仓库目录为同一个地址，需要手动复制出来到外面来执行

## settings.xml 配置

    settings.xml 中需要配置远程仓库中的账号密码
    
```xml
<settings>
    <servers>
        <server>
            <id>maven-mac-repository</id>
            <username>admin</username>
            <password>PkXZxU9XeLbyQrq</password>
        </server>
    </servers>
    <repositories>
        <repository>
        <id>maven-mac-repository</id>
        <name>maven-mac-repository</name>
        <url>http://127.0.0.1:8081/repository/maven-mac-repository/</url>
        </repository>
    </repositories>
</settings>
```

## mac 下执行的上传脚本为

```sh
sh mvn -s /Users/wangmeng/.m2/settings.xml 
deploy:deploy-file 
-Durl=http://127.0.0.1:8081/repository/maven-mac-repository/ 
-DrepositoryId=maven-mac-repository 
-DgeneratePom=false 
-DpomFile=git-commit-id-plugin-2.2.6.pom 
-Dpackaging=jar 
-Dfile=git-commit-id-plugin-2.2.6.jar
```

Windows 下将 sh 改为 cmd /c 应该就可以执行通过
