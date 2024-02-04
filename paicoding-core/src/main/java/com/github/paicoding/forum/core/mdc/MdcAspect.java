package com.github.paicoding.forum.core.mdc;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * This class is an Aspect that handles MDC (Mapped Diagnostic Context) logging.
 * It uses Spring's Aspect Oriented Programming (AOP) capabilities to intercept method calls
 * that are annotated with the custom @MdcDot annotation.
 * The Aspect adds a unique business code to the MDC before the method is executed,
 * and removes it after the method has finished.
 * This allows the business code to be included in all log messages that are generated during the method's execution.
 *
 * @author YiHui
 * @date 2023/5/26
 */
@Slf4j
@Aspect
@Component
public class MdcAspect implements ApplicationContextAware {
    private ExpressionParser parser = new SpelExpressionParser();
    private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * Defines a pointcut for methods that are annotated with @MdcDot.
     * The pointcut also matches if the class of the method is annotated with @MdcDot.
     */
    @Pointcut("@annotation(MdcDot) || @within(MdcDot)")
    public void getLogAnnotation() {
    }

    /**
     * This advice is executed around the method calls that match the pointcut.
     * It adds the business code to the MDC before the method is executed,
     * and removes it after the method has finished.
     * It also logs the execution time of the method.
     *
     * @param joinPoint the join point at which the advice is applied
     * @return the result of the method call
     * @throws Throwable if the method call throws an exception
     */
    @Around("getLogAnnotation()")
    public Object handle(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        boolean hasTag = addMdcCode(joinPoint);
        try {
            Object ans = joinPoint.proceed();
            return ans;
        } finally {
            log.info("执行耗时: {}#{} = {}ms",
                    joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    System.currentTimeMillis() - start);
            if (hasTag) {
                MdcUtil.reset();
            }
        }
    }

    /**
     * Adds the business code to the MDC if the method or its class is annotated with @MdcDot.
     *
     * @param joinPoint the join point at which the advice is applied
     * @return true if the business code was added to the MDC, false otherwise
     */
    private boolean addMdcCode(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        MdcDot dot = method.getAnnotation(MdcDot.class);
        if (dot == null) {
            dot = (MdcDot) joinPoint.getSignature().getDeclaringType().getAnnotation(MdcDot.class);
        }

        if (dot != null) {
            MdcUtil.add("bizCode", loadBizCode(dot.bizCode(), joinPoint));
            return true;
        }
        return false;
    }

    /**
     * Loads the business code from the @MdcDot annotation.
     * If the business code is a SpEL expression, it is evaluated in the context of the method parameters.
     *
     * @param key the business code or SpEL expression
     * @param joinPoint the join point at which the advice is applied
     * @return the business code
     */
    private String loadBizCode(String key, ProceedingJoinPoint joinPoint) {
        if (StringUtils.isBlank(key)) {
            return "";
        }

        StandardEvaluationContext context = new StandardEvaluationContext();

        context.setBeanResolver(new BeanFactoryResolver(applicationContext));
        String[] params = parameterNameDiscoverer.getParameterNames(((MethodSignature) joinPoint.getSignature()).getMethod());
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            context.setVariable(params[i], args[i]);
        }
        return parser.parseExpression(key).getValue(context, String.class);
    }

    private ApplicationContext applicationContext;

    /**
     * Sets the ApplicationContext that this object runs in.
     * Normally this call will be used to initialize the object.
     *
     * @param applicationContext the ApplicationContext object to be used by this object
     * @throws BeansException in case of context initialization errors
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}