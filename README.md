# runelite-external-plugins
Source of my external plugins

Each plugin lives on its own branch. This `master` branch is the base template that
new plugin branches are created from.

## Starting a new plugin

```
git checkout -b my-new-plugin master
```

Then rename everything off the template — leaving `example` in place will get the
plugin rejected from the hub:

- [ ] `src/main/java/io/mark/example/` → `src/main/java/io/mark/<yourplugin>/`
- [ ] `src/test/java/io/mark/example/` → `src/test/java/io/mark/<yourplugin>/`
- [ ] `ExamplePlugin`, `ExampleConfig`, `ExamplePluginLauncher` → your class names
- [ ] `ExampleConfig.CONFIG_GROUP` → a name specific to your plugin
- [ ] `build.gradle` — `group`, `version`, `pluginMainClass`
- [ ] `settings.gradle` — `rootProject.name`
- [ ] `runelite-plugin.properties` — `displayName`, `description`, `tags`, `plugins`, `support`, `version`
- [ ] Delete the placeholder `exampleToggle` config item
- [ ] Add an `icon.png` in the repo root for the hub tile

## Building

```
./gradlew build
```

## Running a development client

```
./gradlew run
```

See [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) for logging in to the
development client.
