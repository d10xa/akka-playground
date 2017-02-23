#!/bin/bash
./gradlew compileScala --quiet && \
java -Dscala.usejavacp=true \
     -classpath "$(./gradlew printClasspath --quiet)" \
     scala.tools.nsc.MainGenericRunner \
     -i repl.scala

# https://chris-martin.org/2015/gradle-scala-repl