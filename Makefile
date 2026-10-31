# Thin wrappers over Gradle (ModDevGradle). Memory is capped for small machines; override with GRADLE_ARGS.
GRADLE ?= ./gradlew
GRADLE_ARGS ?= --no-daemon -Dorg.gradle.jvmargs=-Xmx2g

.PHONY: check build test gametest lint data run-client run-server clean

## check: what CI runs (compile + unit tests + jar)
check:
	$(GRADLE) $(GRADLE_ARGS) build

build:
	$(GRADLE) $(GRADLE_ARGS) assemble

## test: unit tests (JUnit)
test:
	$(GRADLE) $(GRADLE_ARGS) test

## gametest: headless GameTest server (exits non-zero when a required test fails)
gametest:
	$(GRADLE) $(GRADLE_ARGS) runGameTestServer

## lint: no linter is configured yet; javac warnings are the lint
lint:
	$(GRADLE) $(GRADLE_ARGS) compileJava

## data: run datagen into src/generated/resources
data:
	$(GRADLE) $(GRADLE_ARGS) runData

run-client:
	$(GRADLE) $(GRADLE_ARGS) runClient

run-server:
	$(GRADLE) $(GRADLE_ARGS) runServer

clean:
	$(GRADLE) $(GRADLE_ARGS) clean
