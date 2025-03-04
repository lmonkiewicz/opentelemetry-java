plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "OpenTelemetry SDK Auto-configuration"
otelJava.moduleName.set("io.opentelemetry.sdk.autoconfigure")

dependencies {
  api(project(":sdk:all"))
  api(project(":sdk:metrics"))
  api(project(":sdk:logs"))
  api(project(":sdk-extensions:autoconfigure-spi"))

  implementation(project(":semconv"))
  implementation(project(":exporters:common"))

  compileOnly(project(":exporters:jaeger"))
  compileOnly(project(":exporters:logging"))
  compileOnly(project(":exporters:otlp:all"))
  compileOnly(project(":exporters:otlp:logs"))
  compileOnly(project(":exporters:otlp:common"))
  compileOnly(project(":exporters:prometheus"))
  compileOnly(project(":exporters:zipkin"))

  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":sdk:trace-shaded-deps"))

  testImplementation(project(":sdk:testing"))
  testImplementation("com.linecorp.armeria:armeria-junit5")
  testImplementation("com.linecorp.armeria:armeria-grpc")
  testImplementation("edu.berkeley.cs.jqf:jqf-fuzz")
  testRuntimeOnly("io.grpc:grpc-netty-shaded")
  testImplementation("io.opentelemetry.proto:opentelemetry-proto")
}

testing {
  suites {
    val testAutoConfigureOrder by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask {
            environment("OTEL_TRACES_EXPORTER", "none")
            environment("OTEL_METRICS_EXPORTER", "none")
            environment("OTEL_LOGS_EXPORTER", "none")
          }
        }
      }
    }
    val testConditionalResourceProvider by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":semconv"))
      }

      targets {
        all {
          testTask {
            environment("OTEL_TRACES_EXPORTER", "none")
            environment("OTEL_METRICS_EXPORTER", "none")
            environment("OTEL_LOGS_EXPORTER", "none")
          }
        }
      }
    }
    val testConfigError by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":extensions:trace-propagators"))
        implementation(project(":exporters:jaeger"))
        implementation(project(":exporters:logging"))
        implementation(project(":exporters:otlp:all"))
        implementation(project(":exporters:otlp:logs"))
        implementation(project(":exporters:prometheus"))
        implementation(project(":exporters:zipkin"))
      }
    }
    val testFullConfig by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":extensions:trace-propagators"))
        implementation(project(":exporters:jaeger"))
        implementation(project(":exporters:logging"))
        implementation(project(":exporters:otlp:all"))
        implementation(project(":exporters:otlp:logs"))
        implementation(project(":exporters:otlp:common"))
        implementation(project(":exporters:prometheus"))
        implementation(project(":exporters:zipkin"))
        implementation(project(":sdk:testing"))
        implementation(project(":sdk:trace-shaded-deps"))
        implementation(project(":semconv"))

        implementation("com.google.guava:guava")
        implementation("io.opentelemetry.proto:opentelemetry-proto")
        implementation("com.linecorp.armeria:armeria-junit5")
        implementation("com.linecorp.armeria:armeria-grpc")
        runtimeOnly("io.grpc:grpc-netty-shaded")
      }

      targets {
        all {
          testTask {
            environment("OTEL_LOGS_EXPORTER", "otlp")
            environment("OTEL_RESOURCE_ATTRIBUTES", "service.name=test,cat=meow")
            environment("OTEL_PROPAGATORS", "tracecontext,baggage,b3,b3multi,jaeger,ottrace,test")
            environment("OTEL_BSP_SCHEDULE_DELAY", "10")
            environment("OTEL_METRIC_EXPORT_INTERVAL", "10")
            environment("OTEL_EXPORTER_OTLP_HEADERS", "cat=meow,dog=bark")
            environment("OTEL_EXPORTER_OTLP_TIMEOUT", "5000")
            environment("OTEL_SPAN_ATTRIBUTE_COUNT_LIMIT", "2")
            environment("OTEL_TEST_CONFIGURED", "true")
            environment("OTEL_TEST_WRAPPED", "1")
          }
        }
      }
    }
    val testInitializeRegistersGlobal by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask {
            environment("OTEL_TRACES_EXPORTER", "none")
            environment("OTEL_METRICS_EXPORTER", "none")
          }
        }
      }
    }
    val testJaeger by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":exporters:jaeger"))
        implementation(project(":exporters:jaeger-proto"))

        implementation("com.linecorp.armeria:armeria-junit5")
        implementation("com.linecorp.armeria:armeria-grpc")
        runtimeOnly("io.grpc:grpc-netty-shaded")
      }

      targets {
        all {
          testTask {
            environment("OTEL_METRICS_EXPORTER", "none")
            environment("OTEL_TRACES_EXPORTER", "jaeger")
            environment("OTEL_BSP_SCHEDULE_DELAY", "10")
          }
        }
      }
    }
    val testOtlp by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":exporters:otlp:all"))
        implementation(project(":exporters:otlp:logs"))
        implementation(project(":exporters:otlp:common"))
        implementation(project(":sdk:testing"))
        implementation(project(":sdk:logs-testing"))

        implementation("io.opentelemetry.proto:opentelemetry-proto")
        implementation("com.linecorp.armeria:armeria-junit5")
        implementation("com.linecorp.armeria:armeria-grpc")
        implementation("com.squareup.okhttp3:okhttp")
        implementation("com.squareup.okhttp3:okhttp-tls")
        runtimeOnly("io.grpc:grpc-netty-shaded")
      }
    }
    val testPrometheus by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":exporters:prometheus"))

        implementation("com.linecorp.armeria:armeria-junit5")
      }

      targets {
        all {
          testTask {
            environment("OTEL_TRACES_EXPORTER", "none")
            environment("OTEL_METRICS_EXPORTER", "prometheus")
            environment("OTEL_METRIC_EXPORT_INTERVAL", "10")
          }
        }
      }
    }
    val testZipkin by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":exporters:zipkin"))

        implementation("com.linecorp.armeria:armeria-junit5")
      }

      targets {
        all {
          testTask {
            environment("OTEL_METRICS_EXPORTER", "none")
            environment("OTEL_TRACES_EXPORTER", "zipkin")
            environment("OTEL_BSP_SCHEDULE_DELAY", "10")
          }
        }
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
