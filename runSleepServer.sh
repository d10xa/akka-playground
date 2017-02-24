#!/bin/bash
./gradlew compileScala --quiet && \
java -Dscala.usejavacp=true \
     -classpath "$(./gradlew printClasspath --quiet)" \
     ru.d10xa.akkaplayground.SleepServer
