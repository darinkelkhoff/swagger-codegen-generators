package io.swagger.codegen.v3.generators.java;

import io.swagger.codegen.v3.CliOption;
import io.swagger.codegen.v3.CodegenConstants;
import io.swagger.codegen.v3.CodegenModel;
import io.swagger.codegen.v3.CodegenOperation;
import io.swagger.codegen.v3.CodegenParameter;
import io.swagger.codegen.v3.CodegenProperty;
import io.swagger.codegen.v3.CodegenType;
import io.swagger.codegen.v3.SupportingFile;
import io.swagger.codegen.v3.generators.features.BeanValidationFeatures;
import io.swagger.codegen.v3.generators.features.GzipFeatures;
import io.swagger.codegen.v3.generators.features.NotNullAnnotationFeatures;
import io.swagger.codegen.v3.generators.features.PerformBeanValidationFeatures;
import io.swagger.codegen.v3.generators.util.OpenAPIUtil;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static io.swagger.codegen.v3.CodegenConstants.IS_ENUM_EXT_NAME;
import static io.swagger.codegen.v3.generators.handlebars.ExtensionHelper.getBooleanValue;
import static java.util.Collections.sort;

public class JavaClientCodegen extends AbstractJavaCodegen implements BeanValidationFeatures, PerformBeanValidationFeatures, GzipFeatures, NotNullAnnotationFeatures {
    static final String MEDIA_TYPE = "mediaType";

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaClientCodegen.class);

    public static final String USE_RX_JAVA = "useRxJava";
    public static final String USE_RX_JAVA2 = "useRxJava2";
    public static final String USE_RX_JAVA3 = "useRxJava3";
    public static final String DO_NOT_USE_RX = "doNotUseRx";
    public static final String USE_PLAY_WS = "usePlayWS";
    public static final String PLAY_VERSION = "playVersion";
    public static final String PARCELABLE_MODEL = "parcelableModel";
    public static final String USE_RUNTIME_EXCEPTION = "useRuntimeException";

    public static final String PLAY_24 = "play24";
    public static final String PLAY_25 = "play25";

    public static final String RETROFIT_1 = "retrofit";
    public static final String RETROFIT_2 = "retrofit2";

    protected String gradleWrapperPackage = "gradle.wrapper";
    protected boolean useRxJava = false;
    protected boolean useRxJava2 = false;
    protected boolean useRxJava3 = false;
    protected boolean doNotUseRx = true; // backwards compatibility for swagger configs that specify neither rx1 nor rx2 (mustache does not allow for boolean operators so we need this extra field)
    protected boolean usePlayWS = false;
    protected String playVersion = PLAY_25;
    protected boolean parcelableModel = false;
    protected boolean useBeanValidation = false;
    protected boolean performBeanValidation = false;
    protected boolean useGzipFeature = false;
    protected boolean useRuntimeException = false;
    private boolean notNullJacksonAnnotation = false;


    public JavaClientCodegen() {
        super();
        outputFolder = "generated-code" + File.separator + "java";
        invokerPackage = "io.swagger.client";
        artifactId = "swagger-java-client";
        apiPackage = "io.swagger.client.api";
        modelPackage = "io.swagger.client.model";

        cliOptions.add(CliOption.newBoolean(USE_RX_JAVA, "Whether to use the RxJava adapter with the retrofit2 library."));
        cliOptions.add(CliOption.newBoolean(USE_RX_JAVA2, "Whether to use the RxJava2 adapter with the retrofit2 library."));
        cliOptions.add(CliOption.newBoolean(USE_RX_JAVA3, "Whether to use the RxJava3 adapter with the retrofit2 library."));
        cliOptions.add(CliOption.newBoolean(PARCELABLE_MODEL, "Whether to generate models for Android that implement Parcelable with the okhttp-gson library."));
        cliOptions.add(CliOption.newBoolean(USE_PLAY_WS, "Use Play! Async HTTP client (Play WS API)"));
        cliOptions.add(CliOption.newString(PLAY_VERSION, "Version of Play! Framework (possible values \"play24\", \"play25\")"));
        cliOptions.add(CliOption.newBoolean(USE_BEANVALIDATION, "Use BeanValidation API annotations"));
        cliOptions.add(CliOption.newBoolean(PERFORM_BEANVALIDATION, "Perform BeanValidation"));
        cliOptions.add(CliOption.newBoolean(USE_GZIP_FEATURE, "Send gzip-encoded requests"));
        cliOptions.add(CliOption.newBoolean(USE_RUNTIME_EXCEPTION, "Use RuntimeException instead of Exception"));

        supportedLibraries.put("jersey1", "HTTP client: Jersey client 1.19.4. JSON processing: Jackson 2.10.1. Enable gzip request encoding using '-DuseGzipFeature=true'.");
        supportedLibraries.put("feign", "HTTP client: OpenFeign 9.4.0. JSON processing: Jackson 2.10.1");
        supportedLibraries.put("jersey2", "HTTP client: Jersey client 2.26. JSON processing: Jackson 2.10.1");
        supportedLibraries.put("okhttp-gson", "HTTP client: OkHttp 2.7.5. JSON processing: Gson 2.8.1. Enable Parcelable models on Android using '-DparcelableModel=true'. Enable gzip request encoding using '-DuseGzipFeature=true'.");
        supportedLibraries.put("okhttp4-gson", "HTTP client: OkHttp 4.10.0. JSON processing: Gson 2.10.1. Enable Parcelable models on Android using '-DparcelableModel=true'. Enable gzip request encoding using '-DuseGzipFeature=true'.");
        supportedLibraries.put(RETROFIT_1, "HTTP client: OkHttp 2.7.5. JSON processing: Gson 2.3.1 (Retrofit 1.9.0). IMPORTANT NOTE: retrofit1.x is no longer actively maintained so please upgrade to 'retrofit2' instead.");
        supportedLibraries.put(RETROFIT_2, "HTTP client: OkHttp 3.8.0. JSON processing: Gson 2.6.1 (Retrofit 2.3.0). Enable the RxJava adapter using '-DuseRxJava[2]=true'. (RxJava 1.x or 2.x)");
        supportedLibraries.put("resttemplate", "HTTP client: Spring RestTemplate 4.3.9-RELEASE. JSON processing: Jackson 2.9.9");
        supportedLibraries.put("resteasy", "HTTP client: Resteasy client 3.1.3.Final. JSON processing: Jackson 2.9.9");

        CliOption libraryOption = new CliOption(CodegenConstants.LIBRARY, "library template (sub-template) to use");
        libraryOption.setEnum(supportedLibraries);
        // set okhttp-gson as the default
        libraryOption.setDefault("okhttp-gson");
        cliOptions.add(libraryOption);
        setLibrary("okhttp-gson");

    }

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String getName() {
        return "java";
    }

    @Override
    public String getHelp() {
        return "Generates a Java client library.";
    }

    @Override
    public void processOpts() {
        if (RETROFIT_1.equalsIgnoreCase(library)) {
            dateLibrary = "joda";
        }

        super.processOpts();

        if (additionalProperties.containsKey(USE_RX_JAVA)) {
            this.setUseRxJava(Boolean.valueOf(additionalProperties.get(USE_RX_JAVA).toString()));
        }
        if (additionalProperties.containsKey(USE_RX_JAVA2)) {
            this.setUseRxJava2(Boolean.valueOf(additionalProperties.get(USE_RX_JAVA2).toString()));
        }
        if (additionalProperties.containsKey(USE_RX_JAVA3)) {
            this.setUseRxJava3(Boolean.valueOf(additionalProperties.get(USE_RX_JAVA3).toString()));
        }

        if (!useRxJava && !useRxJava2 && !useRxJava3) {
            additionalProperties.put(DO_NOT_USE_RX, true);
        }
        if (additionalProperties.containsKey(USE_PLAY_WS)) {
            this.setUsePlayWS(Boolean.valueOf(additionalProperties.get(USE_PLAY_WS).toString()));
        }
        additionalProperties.put(USE_PLAY_WS, usePlayWS);

        if (additionalProperties.containsKey(PLAY_VERSION)) {
            this.setPlayVersion(additionalProperties.get(PLAY_VERSION).toString());
        }
        additionalProperties.put(PLAY_VERSION, playVersion);

        if (additionalProperties.containsKey(PARCELABLE_MODEL)) {
            this.setParcelableModel(Boolean.valueOf(additionalProperties.get(PARCELABLE_MODEL).toString()));
        }
        // put the boolean value back to PARCELABLE_MODEL in additionalProperties
        additionalProperties.put(PARCELABLE_MODEL, parcelableModel);

        if (additionalProperties.containsKey(USE_BEANVALIDATION)) {
            this.setUseBeanValidation(convertPropertyToBooleanAndWriteBack(USE_BEANVALIDATION));
        }

        if (additionalProperties.containsKey(PERFORM_BEANVALIDATION)) {
            this.setPerformBeanValidation(convertPropertyToBooleanAndWriteBack(PERFORM_BEANVALIDATION));
        }

        if (additionalProperties.containsKey(USE_GZIP_FEATURE)) {
            this.setUseGzipFeature(convertPropertyToBooleanAndWriteBack(USE_GZIP_FEATURE));
        }

        if (additionalProperties.containsKey(USE_RUNTIME_EXCEPTION)) {
            this.setUseRuntimeException(convertPropertyToBooleanAndWriteBack(USE_RUNTIME_EXCEPTION));
        }

        final String invokerFolder = (sourceFolder + File.separator + invokerPackage).replace(".", File.separator);
        final String authFolder = (sourceFolder + File.separator + invokerPackage + ".auth").replace(".", File.separator);
        final String apiFolder = (sourceFolder + File.separator + apiPackage).replace(".", File.separator);

        //Common files
        writeOptional(outputFolder, new SupportingFile("pom.mustache", "", "pom.xml"));
        writeOptional(outputFolder, new SupportingFile("README.mustache", "", "README.md"));
        if (java11Mode) {
            writeOptional(outputFolder, new SupportingFile("build.gradle.java11.mustache", "", "build.gradle"));
        } else {
            writeOptional(outputFolder, new SupportingFile("build.gradle.mustache", "", "build.gradle"));
        }
        writeOptional(outputFolder, new SupportingFile("build.sbt.mustache", "", "build.sbt"));
        writeOptional(outputFolder, new SupportingFile("settings.gradle.mustache", "", "settings.gradle"));
        writeOptional(outputFolder, new SupportingFile("gradle.properties.mustache", "", "gradle.properties"));
        writeOptional(outputFolder, new SupportingFile("manifest.mustache", projectFolder, "AndroidManifest.xml"));
        supportingFiles.add(new SupportingFile("travis.mustache", "", ".travis.yml"));
        supportingFiles.add(new SupportingFile("ApiClient.mustache", invokerFolder, "ApiClient.java"));
        if(!"resttemplate".equals(getLibrary())) {
            supportingFiles.add(new SupportingFile("StringUtil.mustache", invokerFolder, "StringUtil.java"));
        }

        supportingFiles.add(new SupportingFile("auth/HttpBasicAuth.mustache", authFolder, "HttpBasicAuth.java"));
        supportingFiles.add(new SupportingFile("auth/ApiKeyAuth.mustache", authFolder, "ApiKeyAuth.java"));
        supportingFiles.add(new SupportingFile("auth/OAuth.mustache", authFolder, "OAuth.java"));
        supportingFiles.add(new SupportingFile("auth/OAuthFlow.mustache", authFolder, "OAuthFlow.java"));

        supportingFiles.add(new SupportingFile( "gradlew.mustache", "", "gradlew") );
        supportingFiles.add(new SupportingFile( "gradlew.bat.mustache", "", "gradlew.bat") );
        supportingFiles.add(new SupportingFile( "gradle-wrapper.properties.mustache",
                gradleWrapperPackage.replace( ".", File.separator ), "gradle-wrapper.properties") );
        supportingFiles.add(new SupportingFile( "gradle-wrapper.jar",
                gradleWrapperPackage.replace( ".", File.separator ), "gradle-wrapper.jar") );
        supportingFiles.add(new SupportingFile("git_push.sh.mustache", "", "git_push.sh"));
        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));

        if (performBeanValidation) {
            supportingFiles.add(new SupportingFile("BeanValidationException.mustache", invokerFolder,
                    "BeanValidationException.java"));
        }

        //TODO: add doc to retrofit1 and feign
        if ( "feign".equals(getLibrary()) || "retrofit".equals(getLibrary()) ){
            modelDocTemplateFiles.remove("model_doc.mustache");
            apiDocTemplateFiles.remove("api_doc.mustache");
        }

        if (!("feign".equals(getLibrary()) || "resttemplate".equals(getLibrary()) || usesAnyRetrofitLibrary())) {
            supportingFiles.add(new SupportingFile("apiException.mustache", invokerFolder, "ApiException.java"));
            supportingFiles.add(new SupportingFile("Configuration.mustache", invokerFolder, "Configuration.java"));
            supportingFiles.add(new SupportingFile("Pair.mustache", invokerFolder, "Pair.java"));
            supportingFiles.add(new SupportingFile("auth/Authentication.mustache", authFolder, "Authentication.java"));
        }

        if ("feign".equals(getLibrary())) {
            additionalProperties.put("jackson", "true");
            supportingFiles.add(new SupportingFile("ParamExpander.mustache", invokerFolder, "ParamExpander.java"));
            supportingFiles.add(new SupportingFile("EncodingUtils.mustache", invokerFolder, "EncodingUtils.java"));
        } else if ("okhttp-gson".equals(getLibrary()) || "okhttp4-gson".equals(getLibrary()) || StringUtils.isEmpty(getLibrary())) {
            // the "okhttp-gson" library template requires "ApiCallback.mustache" for async call
            supportingFiles.add(new SupportingFile("ApiCallback.mustache", invokerFolder, "ApiCallback.java"));
            supportingFiles.add(new SupportingFile("ApiResponse.mustache", invokerFolder, "ApiResponse.java"));
            supportingFiles.add(new SupportingFile("JSON.mustache", invokerFolder, "JSON.java"));
            supportingFiles.add(new SupportingFile("ProgressRequestBody.mustache", invokerFolder, "ProgressRequestBody.java"));
            supportingFiles.add(new SupportingFile("ProgressResponseBody.mustache", invokerFolder, "ProgressResponseBody.java"));
            supportingFiles.add(new SupportingFile("GzipRequestInterceptor.mustache", invokerFolder, "GzipRequestInterceptor.java"));
            additionalProperties.put("gson", "true");
        } else if (usesAnyRetrofitLibrary()) {
            supportingFiles.add(new SupportingFile("auth/OAuthOkHttpClient.mustache", authFolder, "OAuthOkHttpClient.java"));
            supportingFiles.add(new SupportingFile("CollectionFormats.mustache", invokerFolder, "CollectionFormats.java"));
            additionalProperties.put("gson", "true");
            if ("retrofit2".equals(getLibrary()) && !usePlayWS) {
                supportingFiles.add(new SupportingFile("JSON.mustache", invokerFolder, "JSON.java"));
            }
        } else if ("jersey2".equals(getLibrary()) || "resteasy".equals(getLibrary()))  {
            supportingFiles.add(new SupportingFile("JSON.mustache", invokerFolder, "JSON.java"));
            additionalProperties.put("jackson", "true");
        } else if("jersey1".equals(getLibrary())) {
            additionalProperties.put("jackson", "true");
        } else if("resttemplate".equals(getLibrary())) {
            additionalProperties.put("jackson", "true");
            supportingFiles.add(new SupportingFile("auth/Authentication.mustache", authFolder, "Authentication.java"));
        } else {
            LOGGER.error("Unknown library option (-l/--library): " + getLibrary());
        }

        if (usePlayWS) {
            // remove unsupported auth
            Iterator<SupportingFile> iter = supportingFiles.iterator();
            while (iter.hasNext()) {
                SupportingFile sf = iter.next();
                if (sf.templateFile.startsWith("auth/")) {
                    iter.remove();
                }
            }

            apiTemplateFiles.remove("api.mustache");

            if (PLAY_24.equals(playVersion)) {
                additionalProperties.put(PLAY_24, true);
                apiTemplateFiles.put("play24/api.mustache", ".java");

                supportingFiles.add(new SupportingFile("play24/ApiClient.mustache", invokerFolder, "ApiClient.java"));
                supportingFiles.add(new SupportingFile("play24/Play24CallFactory.mustache", invokerFolder, "Play24CallFactory.java"));
                supportingFiles.add(new SupportingFile("play24/Play24CallAdapterFactory.mustache", invokerFolder,
                        "Play24CallAdapterFactory.java"));
            } else {
                additionalProperties.put(PLAY_25, true);
                apiTemplateFiles.put("play25/api.mustache", ".java");

                supportingFiles.add(new SupportingFile("play25/ApiClient.mustache", invokerFolder, "ApiClient.java"));
                supportingFiles.add(new SupportingFile("play25/Play25CallFactory.mustache", invokerFolder, "Play25CallFactory.java"));
                supportingFiles.add(new SupportingFile("play25/Play25CallAdapterFactory.mustache", invokerFolder, "Play25CallAdapterFactory.java"));
                additionalProperties.put("java8", "true");
            }

            supportingFiles.add(new SupportingFile("play-common/auth/ApiKeyAuth.mustache", authFolder, "ApiKeyAuth.java"));
            supportingFiles.add(new SupportingFile("auth/Authentication.mustache", authFolder, "Authentication.java"));
            supportingFiles.add(new SupportingFile("Pair.mustache", invokerFolder, "Pair.java"));

            additionalProperties.put("jackson", "true");
            additionalProperties.remove("gson");
        }

        if (additionalProperties.containsKey("jackson")) {
            supportingFiles.add(new SupportingFile("RFC3339DateFormat.mustache", invokerFolder, "RFC3339DateFormat.java"));
            if ("threetenbp".equals(dateLibrary) && !usePlayWS) {
                supportingFiles.add(new SupportingFile("CustomInstantDeserializer.mustache", invokerFolder, "CustomInstantDeserializer.java"));
            }
        }
    }

    private boolean usesAnyRetrofitLibrary() {
        return getLibrary() != null && getLibrary().contains(RETROFIT_1);
    }

    private boolean usesRetrofit2Library() {
        return getLibrary() != null && getLibrary().contains(RETROFIT_2);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        super.postProcessOperations(objs);
        if (usesAnyRetrofitLibrary()) {
            Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
            if (operations != null) {
                List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
                for (CodegenOperation operation : ops) {
                    boolean hasConsumes = getBooleanValue(operation, CodegenConstants.HAS_CONSUMES_EXT_NAME);
                    if (hasConsumes) {

                        if (isMultipartType(operation.consumes)) {
                            operation.getVendorExtensions().put(CodegenConstants.IS_MULTIPART_EXT_NAME, Boolean.TRUE);
                        }
                        else {
                            operation.prioritizedContentTypes = prioritizeContentTypes(operation.consumes);
                        }
                    }
                    if (operation.returnType == null) {
                        operation.returnType = "Void";
                    }
                    if (usesRetrofit2Library() && StringUtils.isNotEmpty(operation.path) && operation.path.startsWith("/")){
                        operation.path = operation.path.substring(1);
                    }

                    // sorting operation parameters to make sure path params are parsed before query params
                    if (operation.allParams != null) {
                        sort(operation.allParams, new Comparator<CodegenParameter>() {
                            @Override
                            public int compare(CodegenParameter one, CodegenParameter another) {
                                if (getBooleanValue(one, CodegenConstants.IS_PATH_PARAM_EXT_NAME)
                                        && getBooleanValue(another, CodegenConstants.IS_QUERY_PARAM_EXT_NAME)) {
                                    return -1;
                                }
                                if (getBooleanValue(one, CodegenConstants.IS_QUERY_PARAM_EXT_NAME)
                                        && getBooleanValue(another, CodegenConstants.IS_PATH_PARAM_EXT_NAME)){
                                    return 1;
                                }

                                return 0;
                            }
                        });
                        Iterator<CodegenParameter> iterator = operation.allParams.iterator();
                        while (iterator.hasNext()){
                            CodegenParameter param = iterator.next();
                            param.getVendorExtensions().put(CodegenConstants.HAS_MORE_EXT_NAME, iterator.hasNext());
                        }
                    }
                }
            }

        }

        // camelize path variables for Feign client
        if ("feign".equals(getLibrary())) {
            Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
            List<CodegenOperation> operationList = (List<CodegenOperation>) operations.get("operation");
            for (CodegenOperation op : operationList) {
                String path = op.path;
                String[] items = path.split("/", -1);

                for (int i = 0; i < items.length; ++i) {
                    if (items[i].matches("^\\{(.*)\\}$")) { // wrap in {}
                        // camelize path variable
                        items[i] = "{" + camelize(items[i].substring(1, items[i].length()-1), true) + "}";
                    }
                }
                op.path = StringUtils.join(items, "/");
            }
        }

        return objs;
    }

    @Override
    public String apiFilename(String templateName, String tag) {
        return super.apiFilename(templateName, tag);
    }

    /**
     *  Prioritizes consumes mime-type list by moving json-vendor and json mime-types up front, but
     *  otherwise preserves original consumes definition order.
     *  [application/vnd...+json,... application/json, ..as is..]
     *
     * @param consumes consumes mime-type list
     * @return
     */
    static List<Map<String, String>> prioritizeContentTypes(List<Map<String, String>> consumes) {
        if ( consumes.size() <= 1 )
            return consumes;

        List<Map<String, String>> prioritizedContentTypes = new ArrayList<>(consumes.size());

        List<Map<String, String>> jsonVendorMimeTypes = new ArrayList<>(consumes.size());
        List<Map<String, String>> jsonMimeTypes = new ArrayList<>(consumes.size());

        for ( Map<String, String> consume : consumes) {
            if ( isJsonVendorMimeType(consume.get(MEDIA_TYPE))) {
                jsonVendorMimeTypes.add(consume);
            }
            else if ( isJsonMimeType(consume.get(MEDIA_TYPE))) {
                jsonMimeTypes.add(consume);
            }
            else
                prioritizedContentTypes.add(consume);

            consume.put("hasMore", "true");
        }

        prioritizedContentTypes.addAll(0, jsonMimeTypes);
        prioritizedContentTypes.addAll(0, jsonVendorMimeTypes);

        prioritizedContentTypes.get(prioritizedContentTypes.size()-1).put("hasMore", null);

        return prioritizedContentTypes;
    }

    private static boolean isMultipartType(List<Map<String, String>> consumes) {
        Map<String, String> firstType = consumes.get(0);
        if (firstType != null) {
            if ("multipart/form-data".equals(firstType.get(MEDIA_TYPE))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        boolean isEnum = getBooleanValue(model, IS_ENUM_EXT_NAME);
        if(!BooleanUtils.toBoolean(isEnum)) {
            //final String lib = getLibrary();
            //Needed imports for Jackson based libraries
            if(additionalProperties.containsKey("jackson")) {
                model.imports.add("JsonProperty");
                model.imports.add("JsonValue");
            }
            if(additionalProperties.containsKey("gson")) {
                model.imports.add("SerializedName");
                model.imports.add("TypeAdapter");
                model.imports.add("JsonAdapter");
                model.imports.add("JsonReader");
                model.imports.add("JsonWriter");
                model.imports.add("IOException");
            }
        } else { // enum class
            //Needed imports for Jackson's JsonCreator
            if(additionalProperties.containsKey("jackson")) {
                model.imports.add("JsonValue");
                model.imports.add("JsonCreator");
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> postProcessAllModels(Map<String, Object> objs) {
        Map<String, Object> allProcessedModels = super.postProcessAllModels(objs);
        if(!additionalProperties.containsKey("gsonFactoryMethod")) {
            List<Object> allModels = new ArrayList<Object>();
            for (String name: allProcessedModels.keySet()) {
                Map<String, Object> models = (Map<String, Object>)allProcessedModels.get(name);
                try {
                    allModels.add(((List<Object>) models.get("models")).get(0));
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
            additionalProperties.put("parent", modelInheritanceSupportInGson(allModels));
        }
        return allProcessedModels;
    }

    @Override
    public Map<String, Object> postProcessModelsEnum(Map<String, Object> objs) {
        objs = super.postProcessModelsEnum(objs);
        //Needed import for Gson based libraries
        if (additionalProperties.containsKey("gson")) {
            List<Map<String, String>> imports = (List<Map<String, String>>)objs.get("imports");
            List<Object> models = (List<Object>) objs.get("models");
            for (Object _mo : models) {
                Map<String, Object> mo = (Map<String, Object>) _mo;
                CodegenModel cm = (CodegenModel) mo.get("model");
                // for enum model
                boolean isEnum = getBooleanValue(cm, IS_ENUM_EXT_NAME);
                if (Boolean.TRUE.equals(isEnum) && cm.allowableValues != null) {
                    cm.imports.add(importMapping.get("SerializedName"));
                    Map<String, String> item = new HashMap<String, String>();
                    item.put("import", importMapping.get("SerializedName"));
                    imports.add(item);
                }
            }
        }
        return objs;
    }

    @Override
    public String getArgumentsLocation() {
        return "/arguments/java.yaml";
    }

    @Override
    public String getDefaultTemplateDir() {
        return "Java";
    }

    protected List<Map<String, Object>> modelInheritanceSupportInGson(List<?> allModels) {
        Map<CodegenModel, List<CodegenModel>> byParent = new LinkedHashMap<>();
        for (Object model : allModels) {
            Map entry = (Map) model;
            CodegenModel parent = ((CodegenModel)entry.get("model")).parentModel;
            if(null!= parent) {
                byParent.computeIfAbsent(parent, k -> new LinkedList<>()).add((CodegenModel)entry.get("model"));
            }
        }
        List<Map<String, Object>> parentsList = new ArrayList<>();
        for (Map.Entry<CodegenModel, List<CodegenModel>> parentModelEntry : byParent.entrySet()) {
            CodegenModel parentModel = parentModelEntry.getKey();
            List<Map<String, Object>> childrenList = new ArrayList<>();
            Map<String, Object> parent = new HashMap<>();
            parent.put("classname", parentModel.classname);
            List<CodegenModel> childrenModels = parentModelEntry.getValue();
            for (CodegenModel model : childrenModels) {
                Map<String, Object> child = new HashMap<>();
                child.put("name", model.name);
                child.put("classname", model.classname);
                childrenList.add(child);
            }
            parent.put("children", childrenList);
            parent.put("discriminator", parentModel.discriminator);
            if(parentModel.discriminator != null && parentModel.discriminator.getMapping() != null)
            {
                parentModel.discriminator.getMapping().replaceAll((key, value) -> OpenAPIUtil.getSimpleRef(value));
            }
            parentsList.add(parent);
        }

        return parentsList;
    }

    public void setUseRxJava(boolean useRxJava) {
        this.useRxJava = useRxJava;
        doNotUseRx = false;
    }

    public void setUseRxJava2(boolean useRxJava2) {
        this.useRxJava2 = useRxJava2;
        doNotUseRx = false;
    }

    public void setUseRxJava3(boolean useRxJava3) {
        this.useRxJava3 = useRxJava3;
        doNotUseRx = false;
    }

    public void setDoNotUseRx(boolean doNotUseRx) {
        this.doNotUseRx = doNotUseRx;
    }

    public void setUsePlayWS(boolean usePlayWS) {
        this.usePlayWS = usePlayWS;
    }

    public void setPlayVersion(String playVersion) {
        this.playVersion = playVersion;
    }

    public void setParcelableModel(boolean parcelableModel) {
        this.parcelableModel = parcelableModel;
    }

    public void setUseBeanValidation(boolean useBeanValidation) {
        this.useBeanValidation = useBeanValidation;
    }

    public void setPerformBeanValidation(boolean performBeanValidation) {
        this.performBeanValidation = performBeanValidation;
    }

    public void setUseGzipFeature(boolean useGzipFeature) {
        this.useGzipFeature = useGzipFeature;
    }

    public void setUseRuntimeException(boolean useRuntimeException) {
        this.useRuntimeException = useRuntimeException;
    }

    final private static Pattern JSON_MIME_PATTERN = Pattern.compile("(?i)application\\/json(;.*)?");
    final private static Pattern JSON_VENDOR_MIME_PATTERN = Pattern.compile("(?i)application\\/vnd.(.*)+json(;.*)?");

    /**
     * Check if the given MIME is a JSON MIME.
     * JSON MIME examples:
     *   application/json
     *   application/json; charset=UTF8
     *   APPLICATION/JSON
     */
    static boolean isJsonMimeType(String mime) {
        return mime != null && ( JSON_MIME_PATTERN.matcher(mime).matches());
    }

    /**
     * Check if the given MIME is a JSON Vendor MIME.
     * JSON MIME examples:
     *   application/vnd.mycompany+json
     *   application/vnd.mycompany.resourceA.version1+json
     */
    static boolean isJsonVendorMimeType(String mime) {
        return mime != null && JSON_VENDOR_MIME_PATTERN.matcher(mime).matches();
    }

    @Override
    public void setNotNullJacksonAnnotation(boolean notNullJacksonAnnotation) {
        this.notNullJacksonAnnotation = notNullJacksonAnnotation;
    }

    @Override
    public boolean isNotNullJacksonAnnotation() {
        return notNullJacksonAnnotation;
    }
}
