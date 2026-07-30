### POC for building TS types for Owasp Zap and it's addons

##### available tasks:

- `generateTypes` : what you're looking for
- `syncAddOns` : copy .zap addons from your local `zap-extensions` project
- `cleanAddOns`

##### interesting config values

in gradle.properties:

- zapVersion : pulled from npm
- java2tsVersion : from your local build cache for now
- zapExtensionsDir : location of your `zap-extensions` project

##### output

generated TS files sit at `build/generated/zap-api-types/j2ts/`

### running

```bash
# getting .zap extensions from your local build
./gradlew syncAddOns

./gradlew generateTypes

# copying the types on your node project
# cp build/generated/zap-api-types/j2ts/* ../Zaproxy_typescript_scripting_examples/types/
```

### AI disclaimer

mostly generated with Claude.