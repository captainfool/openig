/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.openig.script;

import static org.forgerock.util.Utils.joinAsString;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import javax.script.ScriptException;

import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.forgerock.openig.config.Environment;

import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;

/**
 * A compiled script.
 */
public final class Script {
    /**
     * Groovy script implementation.
     */
    private static final class GroovyImpl implements Impl {
        private final GroovyScriptEngine engine;
        private final String fileName;

        private GroovyImpl(final GroovyScriptEngine engine, final String fileName) throws ScriptException {
            this.engine = engine;
            this.fileName = fileName;
            // Compile a class for the script, that will trigger a first set of errors for invalid scripts
            try {
                engine.loadScriptByName(fileName);
            } catch (Exception e) {
                throw new ScriptException(e);
            }
        }

        @Override
        public Object run(final Map<String, Object> bindings) throws ScriptException {
            try {
                return engine.run(fileName, new Binding(bindings));
            } catch (final Exception e) {
                throw new ScriptException(e);
            } catch (final Throwable e) {
                throw new ScriptException(new Exception(e));
            }
        }
    }

    private interface Impl {
        Object run(Map<String, Object> bindings) throws ScriptException;
    }

    /**
     * The mime-type for Groovy scripts.
     */
    public static final String GROOVY_MIME_TYPE = "application/x-groovy";

    /**
     * The mime-type for Javascript scripts.
     */
    public static final String JS_MIME_TYPE = "text/javascript";

    private static final String EOL = System.getProperty("line.separator");

    private static final Object INIT_LOCK = new Object();
    /**
     * The groovy script cache directory.
     *
     * @GuardedBy initializationLock
     */
    private static volatile File groovyScriptCacheDir;
    /**
     * The groovy script engine.
     *
     * @GuardedBy initializationLock
     */
    private static volatile GroovyScriptEngine groovyScriptEngine;

    /**
     * Loads a script having the provided content type and file name.
     *
     * @param environment The application environment.
     * @param mimeType The script language mime-type.
     * @param file The location of the script to be loaded.
     * @return The script.
     * @throws ScriptException If the script could not be loaded.
     */
    public static Script fromFile(final Environment environment,
                                  final String mimeType,
                                  final String file) throws ScriptException {
        if (GROOVY_MIME_TYPE.equals(mimeType)) {
            final GroovyScriptEngine engine = getGroovyScriptEngine(environment);
            final Impl impl = new GroovyImpl(engine, file);
            return new Script(impl);
        } else {
            throw new ScriptException("Invalid script mime-type '" + mimeType + "': only '"
                    + GROOVY_MIME_TYPE + "' is supported");
        }
    }

    /**
     * Loads a script having the provided content type and content.
     *
     * @param environment The application environment.
     * @param mimeType The script language mime-type.
     * @param sourceLines The script content.
     * @return The script.
     * @throws ScriptException If the script could not be loaded.
     */
    public static Script fromSource(final Environment environment,
                                    final String mimeType,
                                    final String... sourceLines) throws ScriptException {
        return fromSource(environment, mimeType, joinAsString(EOL, (Object[]) sourceLines));
    }

    /**
     * Loads a script having the provided content type and content.
     *
     * @param environment The application environment.
     * @param mimeType The script language mime-type.
     * @param source The script content.
     * @return The script.
     * @throws ScriptException If the script could not be loaded.
     */
    public static Script fromSource(final Environment environment,
                                    final String mimeType,
                                    final String source) throws ScriptException {
        if (GROOVY_MIME_TYPE.equals(mimeType)) {
            final GroovyScriptEngine engine = getGroovyScriptEngine(environment);
            final File groovyScriptCacheDir = getGroovyScriptCacheDir();
            try {
                final File cachedScript =
                        File.createTempFile("script-", ".groovy", groovyScriptCacheDir);
                cachedScript.deleteOnExit();
                final FileWriter writer = new FileWriter(cachedScript);
                writer.write(source);
                writer.close();
                final Impl impl = new GroovyImpl(engine, cachedScript.toURI().toURL().toString());
                return new Script(impl);
            } catch (final IOException e) {
                throw new ScriptException(e);
            }
        } else {
            throw new ScriptException("Invalid script mime-type '" + mimeType + "': only '"
                    + GROOVY_MIME_TYPE + "' is supported");
        }
    }

    private static File getGroovyScriptCacheDir() throws ScriptException {
        File cacheDir = groovyScriptCacheDir;
        if (cacheDir != null) {
            return cacheDir;
        }

        synchronized (INIT_LOCK) {
            cacheDir = groovyScriptCacheDir;
            if (cacheDir != null) {
                return cacheDir;
            }

            try {
                cacheDir = File.createTempFile("openig-groovy-script-cache-", null);
                cacheDir.delete();
                cacheDir.mkdir();
                cacheDir.deleteOnExit();
            } catch (final IOException e) {
                throw new ScriptException(e);
            }

            // Assign only after having fully initialized the cache directory.
            groovyScriptCacheDir = cacheDir;
            return cacheDir;
        }
    }

    private static GroovyScriptEngine getGroovyScriptEngine(final Environment environment)
            throws ScriptException {
        GroovyScriptEngine engine = groovyScriptEngine;
        if (engine != null) {
            return engine;
        }

        synchronized (INIT_LOCK) {
            engine = groovyScriptEngine;
            if (engine != null) {
                return engine;
            }

            final String classPath = environment.getScriptDirectory("groovy").getAbsolutePath();
            try {
                engine = new GroovyScriptEngine(classPath);
            } catch (final IOException e) {
                throw new ScriptException(e);
            }

            CompilerConfiguration compilerConfiguration = engine.getConfig();
            // Set some defaults imports
            ImportCustomizer importCustomizer = new ImportCustomizer();
            importCustomizer.addImports("org.forgerock.http.Client",
                                        "org.forgerock.http.Filter",
                                        "org.forgerock.http.Handler",
                                        "org.forgerock.http.filter.throttling.ThrottlingRate",
                                        "org.forgerock.http.util.Uris",
                                        "org.forgerock.util.AsyncFunction",
                                        "org.forgerock.util.Function",
                                        "org.forgerock.util.promise.NeverThrowsException",
                                        "org.forgerock.util.promise.Promise",
                                        "org.forgerock.services.context.Context")
                            .addStarImports("org.forgerock.http.protocol");
            compilerConfiguration.addCompilationCustomizers(importCustomizer);

            // Bootstrap the Groovy environment, e.g. add meta-classes.
            final URL bootstrap =
                    Script.class.getClassLoader().getResource("scripts/groovy/bootstrap.groovy");
            try {
                engine.run(bootstrap.toString(), new Binding());
            } catch (Exception e) {
                throw new ScriptException(e);
            }

            // Assign only after having fully initialized the engine.
            groovyScriptEngine = engine;
            return engine;
        }
    }

    private final Impl impl;

    private Script(final Impl impl) {
        this.impl = impl;
    }

    /**
     * Runs this script using the provided named variable bindings.
     *
     * @param bindings
     *            The set of bindings to inject into the script.
     * @return The result returned by the script.
     * @throws ScriptException
     *             If the script failed to execute.
     */
    public Object run(final Map<String, Object> bindings) throws ScriptException {
        return impl.run(bindings);
    }
}
