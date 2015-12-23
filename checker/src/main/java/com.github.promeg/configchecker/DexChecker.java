package com.github.promeg.configchecker;

import soot.*;
import soot.options.Options;
import soot.tagkit.*;
import soot.util.Chain;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;

/**
 * Created by guyacong on 2015/12/23.
 */
public class DexChecker {
    private static final HashMap<Class, String> ENFORCER_ANNOTATIONS = new HashMap<Class, String>();

    static {
        ENFORCER_ANNOTATIONS.put(EnforceBooleanValue.class, getNameForSoot(EnforceBooleanValue.class));
        ENFORCER_ANNOTATIONS.put(EnforceDoubleValue.class, getNameForSoot(EnforceDoubleValue.class));
        ENFORCER_ANNOTATIONS.put(EnforceFloatValue.class, getNameForSoot(EnforceFloatValue.class));
        ENFORCER_ANNOTATIONS.put(EnforceIntValue.class, getNameForSoot(EnforceIntValue.class));
        ENFORCER_ANNOTATIONS.put(EnforceLongValue.class, getNameForSoot(EnforceLongValue.class));
        ENFORCER_ANNOTATIONS.put(EnforceStringValue.class, getNameForSoot(EnforceStringValue.class));
    }

    private static final String ANNOTATION_KEY_VALUE = "value";
    private static final String ANNOTATION_KEY_FLAVOR = "flavor";
    private static final String ANNOTATION_KEY_BUILDTYPE = "buildType";

    private final Set<String> PROCESSED_CLASS_SET = new HashSet<String>(10000);

    private final String  androidJar;
    private final List<String>  dexFiles;
    private final String targetFlavor;
    private final String tartBuildType;


    public DexChecker(String androidJar, List<String> dexFiles, String targetFlavor, String tartBuildType) {
        this.androidJar = androidJar;
        this.dexFiles = dexFiles;
        this.targetFlavor = targetFlavor;
        this.tartBuildType = tartBuildType;
    }

    public void run() {
        System.out.println("prepare.... flavor: " + targetFlavor + "   build type: " + tartBuildType);
        long startTime = System.currentTimeMillis();
        initsoot();
        PackManager.v().getPack("jtp").add(new Transform("jtp.DexCheckerTransform", new DexCheckerTransform()));
        System.out.println("Start  checking       ================ " + dexFiles);
        PackManager.v().runPacks();
        System.out.println("Config check passed!  ================ " + dexFiles);
        System.out.println("Done! Cost " + (System.currentTimeMillis() - startTime)/1000 + "s");
    }

    private void initsoot() {
        Options.v().set_force_android_jar(androidJar);
        Options.v().set_allow_phantom_refs(true);
        Options.v().set_prepend_classpath(true);
        Options.v().set_output_format(Options.output_format_none);
        Options.v().set_src_prec(Options.src_prec_apk);
        Options.v().set_process_dir(dexFiles);
        Options.v().set_debug(false);

        G.v().soot_options_Options().set_verbose(false);
        G.v().out = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {

            }
        });

        Scene.v().loadNecessaryClasses();
    }

    private void checkValueThrow(SootField field, AnnotationTag annotationTag) {
        RealValueHolder realValueHolder = extactRealValue(field);
        EnforceValueHolder enforceValueHolder = extactEnforceValue(annotationTag);

        if (realValueHolder != null && enforceValueHolder != null) {
            if (!realValueHolder.getValueType().equals(enforceValueHolder.getValueType())) {
                throw new ConfigCheckFailException(String.format("Enforce value fail <<<<<< Filed type: %s does not match Annotation type: %s!", realValueHolder.getValueType(), enforceValueHolder.getValueType()));
            }
            if (!targetFlavor.equals(enforceValueHolder.getFlavor())) {
                // flavor doesn't match, no need to check value
                return;
            }
            if (!tartBuildType.equals(enforceValueHolder.getBuildType())) {
                // build type doesn't match, no need to check value
                return;
            }
            if (realValueHolder.getValue().equals(enforceValueHolder.getValue())) {
                System.out.println("Enforce value pass >>>>>>> Filed:  " + field.getDeclaringClass().toString() + "." + field.getName() + "=" + realValueHolder + "     " + enforceValueHolder);
            } else {
                throw new ConfigCheckFailException("Enforce value fail <<<<<< Filed:  " + field.getDeclaringClass().toString() + "." + field.getName() + "=" + realValueHolder + "     " + enforceValueHolder);
            }
        }
    }

    private RealValueHolder extactRealValue(SootField sootField) {
        List<Tag> tags = sootField.getTags();
        for (Tag tag : tags) {
            if (tag instanceof StringConstantValueTag) {
                return new RealValueHolder<String>(((StringConstantValueTag) tag).getStringValue());
            } else if (tag instanceof IntegerConstantValueTag) {
                // maybe boolean or int
                if (sootField.getType() instanceof BooleanType) {
                    // boolean
                    return new RealValueHolder<Boolean>(((IntegerConstantValueTag) tag).getIntValue() == 0 ? false : true);
                } else {
                    // int
                    return new RealValueHolder<Integer>(((IntegerConstantValueTag) tag).getIntValue());
                }
            } else if (tag instanceof DoubleConstantValueTag) {
                return new RealValueHolder<Double>(((DoubleConstantValueTag) tag).getDoubleValue());
            } else if (tag instanceof FloatConstantValueTag) {
                return new RealValueHolder<Float>(((FloatConstantValueTag) tag).getFloatValue());
            } else if (tag instanceof LongConstantValueTag) {
                return new RealValueHolder<Long>(((LongConstantValueTag) tag).getLongValue());
            }
        }
        return null;
    }


    private EnforceValueHolder extactEnforceValue(AnnotationTag annotationTag) {
        EnforceValueHolder enforceValueHolder = null;
        String flavor = null;
        String buildType = null;
        for (AnnotationElem annotationElem : annotationTag.getElems()) {
            if (annotationElem instanceof AnnotationStringElem) {
                if (ANNOTATION_KEY_FLAVOR.equals(annotationElem.getName())) {
                    flavor = ((AnnotationStringElem) annotationElem).getValue();
                } else if (ANNOTATION_KEY_BUILDTYPE.equals(annotationElem.getName())) {
                    buildType = ((AnnotationStringElem) annotationElem).getValue();
                }
            }
        }

        if (ENFORCER_ANNOTATIONS.get(EnforceBooleanValue.class).equals(annotationTag.getType())) {
            // boolean
            Boolean value = null;
            for (AnnotationElem annotationElem : annotationTag.getElems()) {
                if (annotationElem instanceof AnnotationBooleanElem) {
                    if (ANNOTATION_KEY_VALUE.equals(annotationElem.getName())) {
                        value = ((AnnotationBooleanElem) annotationElem).getValue();
                        break;
                    }
                }
            }
            enforceValueHolder = new EnforceValueHolder<Boolean>(value, flavor, buildType);
        } else  if (ENFORCER_ANNOTATIONS.get(EnforceDoubleValue.class).equals(annotationTag.getType())) {
            // double
            Double value = null;
            for (AnnotationElem annotationElem : annotationTag.getElems()) {
                if (annotationElem instanceof AnnotationDoubleElem) {
                    if (ANNOTATION_KEY_VALUE.equals(annotationElem.getName())) {
                        value = ((AnnotationDoubleElem) annotationElem).getValue();
                        break;
                    }
                }
            }
            enforceValueHolder = new EnforceValueHolder<Double>(value, flavor, buildType);
        } else  if (ENFORCER_ANNOTATIONS.get(EnforceFloatValue.class).equals(annotationTag.getType())) {
            // float
            Float value = null;
            for (AnnotationElem annotationElem : annotationTag.getElems()) {
                if (annotationElem instanceof AnnotationFloatElem) {
                    if (ANNOTATION_KEY_VALUE.equals(annotationElem.getName())) {
                        value = ((AnnotationFloatElem) annotationElem).getValue();
                        break;
                    }
                }
            }
            enforceValueHolder = new EnforceValueHolder<Float>(value, flavor, buildType);
        }else  if (ENFORCER_ANNOTATIONS.get(EnforceIntValue.class).equals(annotationTag.getType())) {
            // int
            Integer value = null;
            for (AnnotationElem annotationElem : annotationTag.getElems()) {
                if (annotationElem instanceof AnnotationIntElem) {
                    if (ANNOTATION_KEY_VALUE.equals(annotationElem.getName())) {
                        value = ((AnnotationIntElem) annotationElem).getValue();
                        break;
                    }
                }
            }
            enforceValueHolder = new EnforceValueHolder<Integer>(value, flavor, buildType);
        }else  if (ENFORCER_ANNOTATIONS.get(EnforceLongValue.class).equals(annotationTag.getType())) {
            // long
            Long value = null;
            for (AnnotationElem annotationElem : annotationTag.getElems()) {
                if (annotationElem instanceof AnnotationLongElem) {
                    if (ANNOTATION_KEY_VALUE.equals(annotationElem.getName())) {
                        value = ((AnnotationLongElem) annotationElem).getValue();
                        break;
                    }
                }
            }
            enforceValueHolder = new EnforceValueHolder<Long>(value, flavor, buildType);
        }else  if (ENFORCER_ANNOTATIONS.get(EnforceStringValue.class).equals(annotationTag.getType())) {
            // string
            String value = null;
            for (AnnotationElem annotationElem : annotationTag.getElems()) {
                if (annotationElem instanceof AnnotationStringElem) {
                    if (ANNOTATION_KEY_VALUE.equals(annotationElem.getName())) {
                        value = ((AnnotationStringElem) annotationElem).getValue();
                        break;
                    }
                }
            }
            enforceValueHolder = new EnforceValueHolder<String>(value, flavor, buildType);
        }
        return enforceValueHolder;
    }


    private class DexCheckerTransform extends BodyTransformer {
        @Override
        protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
            SootClass sootClass = body.getMethod().getDeclaringClass();
            if (PROCESSED_CLASS_SET.contains(sootClass.toString())) {
                return;
            }

            PROCESSED_CLASS_SET.add(sootClass.toString());
            //System.out.println("processing:  " + sootClass.toString());
            Chain<SootField> fields = sootClass.getFields();
            for (SootField field : fields) {
                List<Tag> tags = field.getTags();
                for (Tag tag : tags) {
                    if (tag instanceof VisibilityAnnotationTag) {
                        List<AnnotationTag> annotationTags = ((VisibilityAnnotationTag) tag).getAnnotations();
                        if (annotationTags != null) {
                            for (AnnotationTag annotationTag : annotationTags) {
                                checkValueThrow(field, annotationTag);
                            }
                        }
                    }
                }
            }
        }
    }

    static String getNameForSoot(Class clazz) {
        return "L" + clazz.getName().replaceAll("\\.", "/") + ";";
    }

    static class RealValueHolder<T> {
        final T value;
        final String type;

        public RealValueHolder(T value) {
            this.value = value;
            type = value.getClass().toString();
        }

        public T getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value.toString();
        }

        public String getValueType() {
            return type;
        }
    }

    static class EnforceValueHolder<T> {
        final T value;
        final String flavor;
        final String buildType;
        final String type;

        public EnforceValueHolder(T value, String flavor, String buildType) {
            this.value = value;
            this.flavor = flavor;
            this.buildType = buildType;
            type = value.getClass().toString();
        }

        public T getValue() {
            return value;
        }

        public String getFlavor() {
            return flavor;
        }

        public String getBuildType() {
            return buildType;
        }

        @Override
        public String toString() {
            return "EnforceValueAnnotation{" +
                    "value=" + value +
                    ", flavor='" + flavor + '\'' +
                    ", buildType='" + buildType + '\'' +
                    '}';
        }

        public String getValueType() {
            return type;
        }
    }
}

