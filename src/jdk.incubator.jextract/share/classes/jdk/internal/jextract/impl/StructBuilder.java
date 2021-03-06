/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.internal.jextract.impl;

import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.jextract.Declaration;
import jdk.incubator.jextract.Type;

/**
 * This class generates static utilities class for C structs, unions.
 */
class StructBuilder extends JavaSourceBuilder {

    private final JavaSourceBuilder prev;
    private final String parentLayoutFieldName;
    private final MemoryLayout parentLayout;
    private final String structAnno;
    private final String structArrayAnno;
    private final String structPtrAnno;

    StructBuilder(JavaSourceBuilder prev, String className, String parentLayoutFieldName, MemoryLayout parentLayout,
            String pkgName, ConstantHelper constantHelper, AnnotationWriter annotationWriter, Type structType) {
        super(prev.uniqueNestedClassName(className), pkgName, constantHelper);
        this.prev = prev;
        this.parentLayoutFieldName = parentLayoutFieldName;
        this.parentLayout = parentLayout;
        this.structAnno = annotationWriter.getCAnnotation(structType);
        this.structArrayAnno = annotationWriter.getCAnnotation(Type.array(structType));
        this.structPtrAnno = annotationWriter.getCAnnotation(Type.pointer(structType));
    }

    JavaSourceBuilder prev() {
        return prev;
    }

    @Override
    void append(String s) {
        prev.append(s);
    }

    @Override
    void append(char c) {
        prev.append(c);
    }

    @Override
    void append(long l) {
        prev.append(l);
    }

    @Override
    void indent() {
        prev.indent();
    }

    @Override
    void incrAlign() {
        prev.incrAlign();
    }

    @Override
    void decrAlign() {
        prev.decrAlign();
    }

    @Override
    protected String getClassModifiers() {
        return PUB_MODS;
    }

    @Override
    protected void addPackagePrefix() {
        // nested class. containing class has necessary package declaration
    }

    @Override
    protected void addImportSection() {
        // nested class. containing class has necessary imports
    }

    @Override
    JavaSourceBuilder classEnd() {
        emitSizeof();
        emitAllocate();
        emitScopeAllocate();
        emitAllocateArray();
        emitScopeAllocateArray();
        emitAllocatePoiner();
        emitScopeAllocatePointer();
        emitAsRestricted();
        return super.classEnd();
    }

    private String getQualifiedName(String fieldName) {
        return className + "$" + fieldName;
    }

    @Override
    void addVarHandleGetter(String javaName, String nativeName, MemoryLayout layout, Class<?> type) {
        var desc = constantHelper.addFieldVarHandle(getQualifiedName(javaName), nativeName, layout, type, parentLayoutFieldName, parentLayout);
        incrAlign();
        indent();
        append(PUB_MODS + displayName(desc.invocationType().returnType()) + " " + javaName + "$VH() {\n");
        incrAlign();
        indent();
        append("return " + getCallString(desc) + ";\n");
        decrAlign();
        indent();
        append("}\n");
        decrAlign();
    }

    @Override
    void addLayoutGetter(String javaName, MemoryLayout layout) {
        var desc = constantHelper.addLayout(javaName, layout);
        incrAlign();
        indent();
        append(PUB_MODS + displayName(desc.invocationType().returnType()) + " $LAYOUT() {\n");
        incrAlign();
        indent();
        append("return " + getCallString(desc) + ";\n");
        decrAlign();
        indent();
        append("}\n");
        decrAlign();
    }

    @Override
    void addGetter(String javaName, String nativeName, MemoryLayout layout, Class<?> type, String anno) {
        incrAlign();
        indent();
        append(PUB_MODS + " " + anno + " " + type.getSimpleName() + " " + javaName + "$get(" + this.structAnno + " MemorySegment seg) {\n");
        incrAlign();
        indent();
        append("return (" + type.getName() + ")"
                + fieldVarHandleGetCallString(getQualifiedName(javaName), nativeName, layout, type) + ".get(seg);\n");
        decrAlign();
        indent();
        append("}\n");
        decrAlign();

        addIndexGetter(javaName, nativeName, layout, type, anno);
    }

    @Override
    void addSetter(String javaName, String nativeName, MemoryLayout layout, Class<?> type, String anno) {
        incrAlign();
        indent();
        String param = MemorySegment.class.getSimpleName() + " seg";
        append(PUB_MODS + "void " + javaName + "$set(" + this.structAnno + " " + param + ", " + anno + " " + type.getSimpleName() + " x) {\n");
        incrAlign();
        indent();
        append(fieldVarHandleGetCallString(getQualifiedName(javaName), nativeName, layout, type) + ".set(seg, x);\n");
        decrAlign();
        indent();
        append("}\n");
        decrAlign();

        addIndexSetter(javaName, nativeName, layout, type, anno);
    }

    @Override
    void addSegmentGetter(String javaName, String nativeName, MemoryLayout layout) {
        incrAlign();
        indent();
        append(PUB_MODS + "MemorySegment " + javaName + "$slice(MemorySegment seg) {\n");
        incrAlign();
        indent();
        append("return RuntimeHelper.nonCloseableNonTransferableSegment(seg.asSlice(");
        append(parentLayout.byteOffset(MemoryLayout.PathElement.groupElement(nativeName)));
        append(", ");
        append(layout.byteSize());
        append("));\n");
        decrAlign();
        indent();
        append("}\n");
        decrAlign();

    }

    private void emitSizeof() {
        incrAlign();
        indent();
        append(PUB_MODS);
        append("long sizeof() { return $LAYOUT().byteSize(); }\n");
        decrAlign();
    }

    private void emitAllocate() {
        incrAlign();
        indent();
        append(PUB_MODS);
        append(structAnno + " MemorySegment allocate() { return MemorySegment.allocateNative($LAYOUT()); }\n");
        decrAlign();
    }

    private void emitScopeAllocate() {
        incrAlign();
        indent();
        append(PUB_MODS);
        append(structAnno + " MemorySegment allocate(NativeScope scope) { return scope.allocate($LAYOUT()); }\n");
        decrAlign();
    }

    private void emitAllocateArray() {
        incrAlign();
        indent();
        append(PUB_MODS);
        append(structArrayAnno + " MemorySegment allocateArray(int len) {\n");
        incrAlign();
        indent();
        append("return MemorySegment.allocateNative(MemoryLayout.ofSequence(len, $LAYOUT()));\n");
        decrAlign();
        indent();
        append('}');
        decrAlign();
    }

    private void emitScopeAllocateArray() {
        incrAlign();
        indent();
        append(PUB_MODS);
        append(structArrayAnno + " MemorySegment allocateArray(int len, NativeScope scope) {\n");
        incrAlign();
        indent();
        append("return scope.allocate(MemoryLayout.ofSequence(len, $LAYOUT()));\n");
        decrAlign();
        indent();
        append("}\n");
        decrAlign();
    }

    private void emitAllocatePoiner() {
        incrAlign();
        indent();
        append(PUB_MODS);
        append(structPtrAnno + " MemorySegment allocatePointer() {\n");
        incrAlign();
        indent();
        append("return MemorySegment.allocateNative(C_POINTER);\n");
        decrAlign();
        indent();
        append("}\n");
        decrAlign();
    }

    private void emitScopeAllocatePointer() {
        incrAlign();
        indent();
        append(PUB_MODS);
        append(structPtrAnno + " MemorySegment allocatePointer(NativeScope scope) {\n");
        incrAlign();
        indent();
        append("return scope.allocate(C_POINTER);\n");
        decrAlign();
        indent();
        append("}\n");
        decrAlign();
    }

    private void emitAsRestricted() {
        incrAlign();
        indent();
        append(PUB_MODS);
        append(structAnno + " MemorySegment ofAddressRestricted(MemoryAddress addr) { return RuntimeHelper.asArrayRestricted(addr, $LAYOUT(), 1); }\n");
        decrAlign();
    }

    private void addIndexGetter(String javaName, String nativeName, MemoryLayout layout, Class<?> type, String anno) {
        incrAlign();
        indent();
        String params = this.structAnno + " " + MemorySegment.class.getSimpleName() + " seg, long index";
        append(PUB_MODS + " " + anno + " " + type.getSimpleName() + " " + javaName + "$get(" + params + ") {\n");
        incrAlign();
        indent();
        append("return (" + type.getName() + ")"
                + fieldVarHandleGetCallString(getQualifiedName(javaName), nativeName, layout, type) +
                ".get(seg.asSlice(index*sizeof()));\n");
        decrAlign();
        indent();
        append("}\n");
        decrAlign();
    }

    private void addIndexSetter(String javaName, String nativeName, MemoryLayout layout, Class<?> type, String anno) {
        incrAlign();
        indent();
        String params = this.structAnno + " " + MemorySegment.class.getSimpleName() + " seg, long index, " + anno + " " + type.getSimpleName() + " x";
        append(PUB_MODS + "void " + javaName + "$set(" + params + ") {\n");
        incrAlign();
        indent();
        append(fieldVarHandleGetCallString(getQualifiedName(javaName), nativeName, layout, type) +
                ".set(seg.asSlice(index*sizeof()), x);\n");
        decrAlign();
        indent();
        append("}\n");
        decrAlign();
    }

    private String fieldVarHandleGetCallString(String javaName, String nativeName, MemoryLayout layout, Class<?> type) {
        return getCallString(constantHelper.addFieldVarHandle(javaName, nativeName, layout, type, parentLayoutFieldName, parentLayout));
    }
}
