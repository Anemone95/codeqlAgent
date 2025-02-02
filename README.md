Let codeql scan `node_module` path.

# Usage

```base
mvn packages
```
Edit `codeql/codeql/javascript/tools/autobuild.sh`, modify:
```bash
env SEMMLE_DIST="$CODEQL_EXTRACTOR_JAVASCRIPT_ROOT" \
    LGTM_SRC="$(pwd)" \
    "${CODEQL_JAVA_HOME}/bin/java" $jvm_args \
    -cp "$CODEQL_EXTRACTOR_JAVASCRIPT_ROOT/tools/extractor-javascript.jar" \
    com.semmle.js.extractor.AutoBuild
```
Add the agent (change the `THIS_PROJECT_DIR` to actual path)
```bash
env SEMMLE_DIST="$CODEQL_EXTRACTOR_JAVASCRIPT_ROOT" \
    LGTM_SRC="$(pwd)" \
    "${CODEQL_JAVA_HOME}/bin/java" $jvm_args \
    -cp "$CODEQL_EXTRACTOR_JAVASCRIPT_ROOT/tools/extractor-javascript.jar" \
    -javaagent: ${THIS_PROJECT_DIR}/target/codeqlagent-1.0-SNAPSHOT.jar \
    com.semmle.js.extractor.AutoBuild
```
Support CodeQL toolchain version: <=2.20.1. Beyond that I haven't test but maybe still work.
