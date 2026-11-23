# Thin wrappers over Gradle (ModDevGradle). Memory is capped for small machines; override with GRADLE_ARGS.
GRADLE ?= ./gradlew
GRADLE_ARGS ?= --no-daemon -Dorg.gradle.jvmargs=-Xmx2g

.PHONY: check build test gametest lint data run-client run-server clean screens screens-approve

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

SCREENS_DIRS = earlywindow/build/screens build/screens
# WebGUI is a required client dependency (compileOnly in Gradle); same file as Provisioning's launcher lock.
WEBGUI_JAR = run/screens/mods/webgui-neoforge-2.0.0+mc1.21.1.jar
WEBGUI_URL = https://cdn.modrinth.com/data/tDw1xQja/versions/Vq4172Jo/webgui-neoforge-2.0.0%2Bmc1.21.1.jar
WEBGUI_SHA1 = 415479a12c8a541f3cb7604bc29d9aefc811fd22

## screens: spec 007 screen tests (needs a display: xvfb-run -a -s "-screen 0 1920x1080x24" make screens)
screens:
	rm -rf $(SCREENS_DIRS) build/screens-report
	$(GRADLE) $(GRADLE_ARGS) :earlywindow:jar :earlywindow:visualTest
	mkdir -p run/screens/mods run/screens/config
	cp earlywindow/build/libs/arffornia-earlywindow.jar run/screens/mods/
	test -f $(WEBGUI_JAR) || curl -fsSL -o $(WEBGUI_JAR) $(WEBGUI_URL)
	echo "$(WEBGUI_SHA1)  $(WEBGUI_JAR)" | sha1sum -c --quiet
	printf 'earlyWindowProvider = "arffornia"\nearlyWindowFBScale = 2\nearlyWindowWidth = 1920\nearlyWindowHeight = 1080\n' > run/screens/config/fml.toml
	printf 'onboardAccessibility:false\nlang:en_us\n' > run/screens/options.txt
	mkdir -p run/screens/arffornia
	printf '{"id":1,"title":"Season 2 opens Friday","date":"2026-11-20","url":"http://127.0.0.1:8000/news/1"}' > run/screens/arffornia/news.json
	$(GRADLE) $(GRADLE_ARGS) runClientScreens
	python3 tools/screens/screens.py compare build/screens-report $(SCREENS_DIRS)

## screens-approve: copy the last screens run into src/test/screens/baselines
screens-approve:
	python3 tools/screens/screens.py approve $(SCREENS_DIRS)
