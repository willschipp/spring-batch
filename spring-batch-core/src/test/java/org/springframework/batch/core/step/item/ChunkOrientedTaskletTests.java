/*
 * Copyright 2006-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.batch.core.step.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * @author Dave Syer
 *
 */
public class ChunkOrientedTaskletTests {

	private ChunkContext context = new ChunkContext(null);

	@Test
	public void testHandle() throws Exception {
		ChunkOrientedTasklet<String> handler = new ChunkOrientedTasklet<String>(new ChunkProvider<String>() {
			@Override
			public Chunk<String> provide(StepContribution contribution) throws Exception {
				contribution.incrementReadCount();
				Chunk<String> chunk = new Chunk<String>();
				chunk.add("foo");
				return chunk;
			}
			@Override
			public void postProcess(StepContribution contribution, Chunk<String> chunk) {};
		}, new ChunkProcessor<String>() {
			@Override
			public void process(StepContribution contribution, Chunk<String> chunk) {
				contribution.incrementWriteCount(1);
			}
		});
		StepContribution contribution = new StepContribution(new StepExecution("foo", new JobExecution(new JobInstance(
				123L, "job"),new JobParameters())));
		handler.execute(contribution, context);
		assertEquals(1, contribution.getReadCount());
		assertEquals(1, contribution.getWriteCount());
		assertEquals(0, context.attributeNames().length);
	}

	@Test
	public void testFail() throws Exception {
		ChunkOrientedTasklet<String> handler = new ChunkOrientedTasklet<String>(new ChunkProvider<String>() {
			@Override
			public Chunk<String> provide(StepContribution contribution) throws Exception {
				throw new RuntimeException("Foo!");
			}
			@Override
			public void postProcess(StepContribution contribution, Chunk<String> chunk) {};
		}, new ChunkProcessor<String>() {
			@Override
			public void process(StepContribution contribution, Chunk<String> chunk) {
				fail("Not expecting to get this far");
			}
		});
		StepContribution contribution = new StepContribution(new StepExecution("foo", new JobExecution(new JobInstance(
				123L, "job"), new JobParameters())));
		try {
			handler.execute(contribution, context);
			fail("Expected RuntimeException");
		}
		catch (RuntimeException e) {
			assertEquals("Foo!", e.getMessage());
		}
		assertEquals(0, contribution.getReadCount());
	}

	@Test
	public void testExitCode() throws Exception {
		ChunkOrientedTasklet<String> handler = new ChunkOrientedTasklet<String>(new ChunkProvider<String>() {
			@Override
			public Chunk<String> provide(StepContribution contribution) throws Exception {
				contribution.incrementReadCount();
				Chunk<String> chunk = new Chunk<String>();
				chunk.add("foo");
				chunk.setEnd();
				return chunk;
			}
			@Override
			public void postProcess(StepContribution contribution, Chunk<String> chunk) {};
		}, new ChunkProcessor<String>() {
			@Override
			public void process(StepContribution contribution, Chunk<String> chunk) {
				contribution.incrementWriteCount(1);
			}
		});
		StepContribution contribution = new StepContribution(new StepExecution("foo", new JobExecution(new JobInstance(
				123L, "job"), new JobParameters())));
		ExitStatus expected = contribution.getExitStatus();
		handler.execute(contribution, context);
		// The tasklet does not change the exit code
		assertEquals(expected, contribution.getExitStatus());
	}
	
	@Test
	public void testStopped() throws Exception {
		ChunkOrientedTasklet<String> handler = new ChunkOrientedTasklet<String>(new MockChunkProvider(), new ChunkProcessor<String>() {
			@Override
			public void process(StepContribution contribution, Chunk<String> chunk) {
				contribution.incrementWriteCount(1);
			}
		});
		StepContribution contribution = new StepContribution(new StepExecution("foo", new JobExecution(new JobInstance(
				123L, "job"), new JobParameters())));
		ExitStatus expected = ExitStatus.STOPPED;
		while (handler.execute(contribution, context).equals(RepeatStatus.CONTINUABLE)) {
			handler.stop();//call a stop after the first one
		}//end while
		// The tasklet does not change the exit code
		assertEquals(expected, contribution.getExitStatus());		
	}

	class MockChunkProvider implements ChunkProvider<String>{

		int max = 3;
		int counter = 0;
		
		@Override
		public Chunk<String> provide(StepContribution contribution) throws Exception {
			while (counter < max) {
				counter++;
				Chunk<String> chunk = new Chunk<String>();
				chunk.add("foo");
				System.out.println("here's the chunk " + chunk);
				return chunk;//send it
			}
			return null;
		}

		@Override
		public void postProcess(StepContribution contribution,Chunk<String> chunk) { }
		
	}
}
