/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.commandhandling;

import org.axonframework.commandhandling.callbacks.AbstractCallback;
import org.junit.*;
import org.mockito.*;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Allard Buijze
 */
public class SimpleCommandBusTest {

    private SimpleCommandBus testSubject;

    @Before
    public void setUp() {
        this.testSubject = new SimpleCommandBus();
    }

    @Test
    public void testDispatchCommand_HandlerSubscribed() {
        testSubject.subscribe(String.class, new MyStringCommandHandler());
        testSubject.dispatch("Say hi!", new AbstractCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                assertEquals("Say hi!", result);
            }

            @Override
            public void onFailure(Throwable cause) {
                fail("Did not expect exception");
            }
        });
    }

    @Test(expected = NoHandlerForCommandException.class)
    public void testDispatchCommand_NoHandlerSubscribed() throws Exception {
        testSubject.dispatch("Say hi!");
    }

    @Test(expected = NoHandlerForCommandException.class)
    public void testDispatchCommand_HandlerUnsubscribed() throws Exception {
        MyStringCommandHandler commandHandler = new MyStringCommandHandler();
        testSubject.subscribe(String.class, commandHandler);
        testSubject.unsubscribe(String.class, commandHandler);
        testSubject.dispatch("Say hi!");
    }

    @Test
    public void testUnsubscribe_HandlerNotKnown() {
        testSubject.unsubscribe(String.class, new MyStringCommandHandler());
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testInterceptorChain_CommandHandledSuccessfully() throws Throwable {
        CommandHandlerInterceptor mockInterceptor1 = mock(CommandHandlerInterceptor.class);
        CommandHandlerInterceptor mockInterceptor2 = mock(CommandHandlerInterceptor.class);
        testSubject.setInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2));
        CommandHandler<String> commandHandler = mock(CommandHandler.class);
        when(commandHandler.handle("Hi there!")).thenReturn("Hi there!");
        testSubject.subscribe(String.class, commandHandler);

        testSubject.dispatch("Hi there!", new AbstractCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                assertEquals("Hi there!", result);
            }

            @Override
            public void onFailure(Throwable cause) {
                fail("Did not expect failure");
            }
        });

        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2, commandHandler);
        inOrder.verify(mockInterceptor1).beforeCommandHandling(isA(CommandContext.class), isA(CommandHandler.class));
        inOrder.verify(mockInterceptor2).beforeCommandHandling(isA(CommandContext.class), isA(CommandHandler.class));
        inOrder.verify(commandHandler).handle("Hi there!");
        inOrder.verify(mockInterceptor2).afterCommandHandling(isA(CommandContext.class), isA(CommandHandler.class));
        inOrder.verify(mockInterceptor1).afterCommandHandling(isA(CommandContext.class), isA(CommandHandler.class));
    }

    @Test
    public void testInterceptorChain_CommandHandlerThrowsException() throws Throwable {
        CommandHandlerInterceptor mockInterceptor1 = mock(CommandHandlerInterceptor.class);
        CommandHandlerInterceptor mockInterceptor2 = mock(CommandHandlerInterceptor.class);
        testSubject.setInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2));
        CommandHandler<String> commandHandler = mock(CommandHandler.class);
        when(commandHandler.handle("Hi there!")).thenThrow(new RuntimeException("Faking failed command handling"));
        testSubject.subscribe(String.class, commandHandler);

        testSubject.dispatch("Hi there!", new AbstractCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                fail("Expected exception to be thrown");
            }

            @Override
            public void onFailure(Throwable cause) {
                assertEquals("Faking failed command handling", cause.getMessage());
            }
        });

        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2, commandHandler);
        inOrder.verify(mockInterceptor1).beforeCommandHandling(isA(CommandContext.class), isA(CommandHandler.class));
        inOrder.verify(mockInterceptor2).beforeCommandHandling(isA(CommandContext.class), isA(CommandHandler.class));
        inOrder.verify(commandHandler).handle("Hi there!");
        inOrder.verify(mockInterceptor2).afterCommandHandling(isA(CommandContext.class), isA(CommandHandler.class));
        inOrder.verify(mockInterceptor1).afterCommandHandling(isA(CommandContext.class), isA(CommandHandler.class));
    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    public void testInterceptorChain_InterceptorThrowsExceptionInAfterHandle() throws Throwable {
        CommandHandlerInterceptor mockInterceptor1 = mock(CommandHandlerInterceptor.class);
        CommandHandlerInterceptor mockInterceptor2 = mock(CommandHandlerInterceptor.class);
        testSubject.setInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2));
        CommandHandler<String> commandHandler = mock(CommandHandler.class);
        when(commandHandler.handle("Hi there!")).thenReturn("Hi there!");
        testSubject.subscribe(String.class, commandHandler);
        RuntimeException someException = new RuntimeException("Mocking");
        doThrow(someException).when(mockInterceptor2).afterCommandHandling(isA(CommandContext.class),
                                                                           isA(CommandHandler.class));
        testSubject.dispatch("Hi there!", new AbstractCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                fail("Expected exception to be propagated");
            }

            @Override
            public void onFailure(Throwable cause) {
                assertEquals("Mocking", cause.getMessage());
            }
        });
        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2, commandHandler);
        inOrder.verify(mockInterceptor1).beforeCommandHandling(isA(CommandContext.class), isA(CommandHandler.class));
        inOrder.verify(mockInterceptor2).beforeCommandHandling(isA(CommandContext.class), isA(CommandHandler.class));
        inOrder.verify(commandHandler).handle("Hi there!");
        inOrder.verify(mockInterceptor2).afterCommandHandling(isA(CommandContext.class), isA(CommandHandler.class));
        inOrder.verify(mockInterceptor1).afterCommandHandling(isA(CommandContext.class), isA(CommandHandler.class));

    }

    @SuppressWarnings({"ThrowableInstanceNeverThrown"})
    @Test
    public void testInterceptorChain_InterceptorThrowsException() throws Exception {
        CommandHandlerInterceptor mockInterceptor1 = mock(CommandHandlerInterceptor.class);
        CommandHandlerInterceptor mockInterceptor2 = mock(CommandHandlerInterceptor.class);
        CommandHandlerInterceptor mockInterceptor3 = mock(CommandHandlerInterceptor.class);
        testSubject.setInterceptors(Arrays.asList(mockInterceptor1, mockInterceptor2, mockInterceptor3));
        MyStringCommandHandler commandHandler = mock(MyStringCommandHandler.class);
        testSubject.subscribe(String.class, commandHandler);
        RuntimeException expected = new RuntimeException("Mocking");
        doThrow(expected).when(mockInterceptor2).beforeCommandHandling(isA(CommandContext.class),
                                                                       isA(CommandHandler.class));
        try {
            testSubject.dispatch("Hi there!");
            fail("Expected exception to be thrown");
        }
        catch (Exception actual) {
            assertSame(expected, actual);
        }
        verifyZeroInteractions(commandHandler);
        verifyZeroInteractions(mockInterceptor3);
        InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2);
        inOrder.verify(mockInterceptor1).beforeCommandHandling(isA(CommandContext.class), same(commandHandler));
        inOrder.verify(mockInterceptor2).beforeCommandHandling(isA(CommandContext.class), same(commandHandler));
        inOrder.verify(mockInterceptor2).afterCommandHandling(isA(CommandContext.class), same(commandHandler));
        inOrder.verify(mockInterceptor1).afterCommandHandling(isA(CommandContext.class), same(commandHandler));
    }

    private static class MyStringCommandHandler implements CommandHandler<String> {

        @Override
        public Object handle(String command) {
            return command;
        }
    }
}