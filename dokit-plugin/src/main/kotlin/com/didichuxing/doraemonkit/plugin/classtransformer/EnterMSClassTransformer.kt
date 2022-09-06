package com.didichuxing.doraemonkit.plugin.classtransformer

import com.didichuxing.doraemonkit.plugin.*
import com.didichuxing.doraemonkit.plugin.extension.SlowMethodExt
import com.didichuxing.doraemonkit.plugin.stack_method.MethodStackNode
import com.didichuxing.doraemonkit.plugin.stack_method.MethodStackNodeUtil
import com.didiglobal.booster.annotations.Priority
import com.didiglobal.booster.transform.TransformContext
import com.didiglobal.booster.transform.asm.ClassTransformer
import com.didiglobal.booster.transform.asm.asIterable
import com.didiglobal.booster.transform.asm.className
import com.google.auto.service.AutoService
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*

/**
 * ================================================
 * 作    者：jint（金台）
 * 版    本：1.0
 * 创建日期：2020/5/14-18:07
 * 描    述：入口函数 慢函数调用栈 wiki:https://juejin.im/post/5e8d87c4f265da47ad218e6b
 * 修订历史：不要指定自动注入 需要手动在DoKitAsmTransformer中通过配置创建
 * 原理:transform()方法的调用是无序的  原因:哪一个class会先被transformer执行是不确定的  但是每一个class被transformer执行顺序是遵循transformer的Priority规则的
 * ================================================
 */
//@Priority(3)
//@AutoService(ClassTransformer::class)
class EnterMSClassTransformer : AbsClassTransformer() {

    private val thresholdTime = DoKitExtUtil.slowMethodExt.stackMethod.thresholdTime
    private val level = 0
    override fun transform(context: TransformContext, klass: ClassNode): ClassNode {


        if (onCommInterceptor(context, klass)) {
            "onCommInterceptor".println()
            return klass
        }

        if (!DoKitExtUtil.dokitSlowMethodSwitchOpen()) {
            "DoKitExtUtil.dokitSlowMethodSwitchOpen false".println()
            return klass
        }

        if (DoKitExtUtil.SLOW_METHOD_STRATEGY == SlowMethodExt.STRATEGY_NORMAL) {
            "SlowMethodExt.STRATEGY_NORMAL 1".println()
            return klass
        }

        if (DoKitExtUtil.ignorePackageNames(klass.className)) {
//            "ignorePackageNames ${klass.className}".println()
            return klass
        }

        //默认为Application onCreate 和attachBaseContext
        val enterMethods = DoKitExtUtil.slowMethodExt.stackMethod.enterMethods

        if (enterMethods.isNotEmpty()) {
            enterMethods.forEach { enterMethodName ->
                klass.methods.forEach { methodNode ->
                    val allMethodName = "${klass.className}.${methodNode.name}"
                    if (allMethodName == enterMethodName) {
                        "${context.projectDir.lastPath()}->level-->$level mathched enterMethod===>$allMethodName".println()
                        operateMethodInsn(klass, methodNode)
                    }
                }
            }
        }

        return klass
    }


    private fun operateMethodInsn(klass: ClassNode, methodNode: MethodNode) {
        //读取全是函数调用的指令
        methodNode.instructions.asIterable().filterIsInstance(MethodInsnNode::class.java)
            .filter { methodInsnNode ->
                methodInsnNode.name != "<init>"
            }.forEach { methodInsnNode ->
                val methodStackNode = MethodStackNode(
                    level,
                    methodInsnNode.ownerClassName,
                    methodInsnNode.name,
                    methodInsnNode.desc,
                    klass.className,
                    methodNode.name,
                    methodNode.desc
                )
                MethodStackNodeUtil.addMethodStackNode(level, methodStackNode)
            }
        //函数出入口插入耗时统计代码
        //方法入口插入
        methodNode.instructions.insert(
            createMethodEnterInsnList(
                level,
                klass.className,
                methodNode.name,
                methodNode.desc,
                methodNode.access
            )
        )
        //方法出口插入
        methodNode.instructions.getMethodExitInsnNodes()?.forEach { methodExitInsnNode ->
            methodNode.instructions.insertBefore(
                methodExitInsnNode,
                createMethodExitInsnList(
                    level,
                    klass.className,
                    methodNode.name,
                    methodNode.desc,
                    methodNode.access
                )
            )
        }
    }


    /**
     * 创建慢函数入口指令集
     */
    private fun createMethodEnterInsnList(
        level: Int,
        className: String,
        methodName: String,
        desc: String,
        access: Int
    ): InsnList {
        val isStaticMethod = access and ACC_STATIC != 0
        return with(InsnList()) {
            if (isStaticMethod) {
                add(
                    FieldInsnNode(
                        GETSTATIC,
                        "com/didichuxing/doraemonkit/aop/method_stack/MethodStackUtil",
                        "INSTANCE",
                        "Lcom/didichuxing/doraemonkit/aop/method_stack/MethodStackUtil;"
                    )
                )
                add(IntInsnNode(BIPUSH, DoKitExtUtil.STACK_METHOD_LEVEL))
                add(IntInsnNode(BIPUSH, thresholdTime))
                add(IntInsnNode(BIPUSH, level))
                add(LdcInsnNode(className))
                add(LdcInsnNode(methodName))
                add(LdcInsnNode(desc))
                add(
                    MethodInsnNode(
                        INVOKEVIRTUAL,
                        "com/didichuxing/doraemonkit/aop/method_stack/MethodStackUtil",
                        "recodeStaticMethodCostStart",
                        "(IIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                        false
                    )
                )
            } else {
                add(
                    FieldInsnNode(
                        GETSTATIC,
                        "com/didichuxing/doraemonkit/aop/method_stack/MethodStackUtil",
                        "INSTANCE",
                        "Lcom/didichuxing/doraemonkit/aop/method_stack/MethodStackUtil;"
                    )
                )
                add(IntInsnNode(BIPUSH, DoKitExtUtil.STACK_METHOD_LEVEL))
                add(IntInsnNode(BIPUSH, thresholdTime))
                add(IntInsnNode(BIPUSH, level))
                add(LdcInsnNode(className))
                add(LdcInsnNode(methodName))
                add(LdcInsnNode(desc))
                add(VarInsnNode(ALOAD, 0))
                add(
                    MethodInsnNode(
                        INVOKEVIRTUAL,
                        "com/didichuxing/doraemonkit/aop/method_stack/MethodStackUtil",
                        "recodeObjectMethodCostStart",
                        "(IIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                        false
                    )
                )
            }
            this
        }


    }


    /**
     * 创建慢函数退出时的指令集
     */
    private fun createMethodExitInsnList(
        level: Int,
        className: String,
        methodName: String,
        desc: String,
        access: Int
    ): InsnList {
        val isStaticMethod = access and ACC_STATIC != 0

        return with(InsnList()) {
            if (isStaticMethod) {
                add(
                    FieldInsnNode(
                        GETSTATIC,
                        "com/didichuxing/doraemonkit/aop/method_stack/MethodStackUtil",
                        "INSTANCE",
                        "Lcom/didichuxing/doraemonkit/aop/method_stack/MethodStackUtil;"
                    )
                )
                add(IntInsnNode(BIPUSH, thresholdTime))
                add(IntInsnNode(BIPUSH, level))
                add(LdcInsnNode(className))
                add(LdcInsnNode(methodName))
                add(LdcInsnNode(desc))
                add(
                    MethodInsnNode(
                        INVOKEVIRTUAL,
                        "com/didichuxing/doraemonkit/aop/method_stack/MethodStackUtil",
                        "recodeStaticMethodCostEnd",
                        "(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                        false
                    )
                )
            } else {
                add(
                    FieldInsnNode(
                        GETSTATIC,
                        "com/didichuxing/doraemonkit/aop/method_stack/MethodStackUtil",
                        "INSTANCE",
                        "Lcom/didichuxing/doraemonkit/aop/method_stack/MethodStackUtil;"
                    )
                )
                add(IntInsnNode(BIPUSH, thresholdTime))
                add(IntInsnNode(BIPUSH, level))
                add(LdcInsnNode(className))
                add(LdcInsnNode(methodName))
                add(LdcInsnNode(desc))
                add(VarInsnNode(ALOAD, 0))
                add(
                    MethodInsnNode(
                        INVOKEVIRTUAL,
                        "com/didichuxing/doraemonkit/aop/method_stack/MethodStackUtil",
                        "recodeObjectMethodCostEnd",
                        "(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
                        false
                    )
                )
            }
            this
        }

    }

}

