package io.mark.hditemicons.hd;

import io.mark.hditemicons.CustomRotation;
import io.mark.hditemicons.CustomRotationStorage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static io.mark.hditemicons.hd.TestPacks.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ModelPackStorageTest {
	private final BlockingQueue<Runnable> clientQueue = new LinkedBlockingQueue<>();
	private final AtomicReference<byte[]> savedBytes = new AtomicReference<>();
	private final AtomicReference<byte[]> temporaryBytes = new AtomicReference<>();
	private final AtomicInteger publications = new AtomicInteger();
	private final Filepath directory = mock(Filepath.class);
	private final CustomRotationStorage rotations = mock(CustomRotationStorage.class);
	private ModelPackStorage storage;

	@Before
	public void start() throws Exception {
		ClientThread clientThread = mock(ClientThread.class);
		doAnswer(call -> {
			clientQueue.add(call.getArgument(0));
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		Filepath saved = mock(Filepath.class), temporary = mock(Filepath.class);
		when(directory.joinSegment("custom-model-pack.zip")).thenReturn(saved);
		when(directory.createTempFile(anyString(), anyString())).thenReturn(temporary);
		when(saved.isFile()).thenAnswer(call -> savedBytes.get() != null);
		when(saved.openInputStream()).thenAnswer(call -> new ByteArrayInputStream(savedBytes.get()));
		doAnswer(call -> { temporaryBytes.set(call.getArgument(0)); return null; })
			.when(temporary).write(any(byte[].class));
		doAnswer(call -> { savedBytes.set(temporaryBytes.get()); return null; })
			.when(temporary).moveTo(eq(saved), any(), any());
		doAnswer(call -> { savedBytes.set(null); return null; }).when(saved).deleteIfExists();
		when(rotations.all()).thenReturn(Map.of());
		storage = new ModelPackStorage(GSON, clientThread, rotations);
		storage.start(directory, publications::incrementAndGet);
		nextPublication().run();
	}

	@After
	public void stop() {
		storage.stop();
	}

	@Test
	public void invalidImportKeepsLastGoodModelAndPersistedArchive() throws Exception {
		importValid();
		ModelPack.Entry previous = storage.get(ItemID.ABYSSAL_WHIP);
		byte[] previousBytes = savedBytes.get();
		CompletableFuture<String> error = new CompletableFuture<>();
		storage.setListener(() -> {
			if (storage.status().startsWith("Model pack error:"))
				error.complete(storage.status());
		});
		storage.importPack(source(new byte[]{1, 2, 3}));
		assertTrue(error.get(5, TimeUnit.SECONDS).contains("Previous pack retained"));
		assertSame(previous, storage.get(ItemID.ABYSSAL_WHIP));
		assertSame(previousBytes, savedBytes.get());
	}

	@Test
	public void savedPackLoadsAfterRestartAndRemovalClearsIt() throws Exception {
		importValid();
		String fingerprint = storage.fingerprint();
		storage.stop();
		storage.start(directory, publications::incrementAndGet);
		nextPublication().run();
		assertEquals(fingerprint, storage.fingerprint());
		assertTrue(storage.hasPack());
		storage.removePack();
		nextPublication().run();
		assertFalse(storage.hasPack());
		assertEquals("", storage.fingerprint());
		assertNull(savedBytes.get());
	}

	@Test
	public void shutdownDiscardsQueuedClientThreadPublication() throws Exception {
		storage.importPack(source(pack("")));
		Runnable late = nextPublication();
		int before = publications.get();
		storage.stop();
		late.run();
		assertFalse(storage.hasPack());
		assertEquals(before, publications.get());
	}

	@Test
	public void exportIncludesModelAssetsAndCurrentEditorCamera() throws Exception {
		importValid();
		CustomRotation camera = new CustomRotation(128, 256, 0, 2100, 2, 3, 128, 128, 128);
		when(rotations.all()).thenReturn(Map.of(ItemID.ABYSSAL_WHIP, camera));
		Filepath target = mock(Filepath.class);
		CompletableFuture<byte[]> exported = new CompletableFuture<>();
		doAnswer(call -> { exported.complete(call.getArgument(0)); return null; }).when(target).write(any(byte[].class));
		storage.exportPack(target);
		ModelPack reimported = ModelPack.parse(exported.get(5, TimeUnit.SECONDS), GSON);
		assertEquals(camera, reimported.items.get(ItemID.ABYSSAL_WHIP).camera);
		assertEquals(3, reimported.items.get(ItemID.ABYSSAL_WHIP).model.vertexX.length);
	}

	private void importValid() throws Exception {
		storage.importPack(source(pack("")));
		nextPublication().run();
		assertTrue(storage.hasPack());
	}

	private Runnable nextPublication() throws InterruptedException {
		Runnable next = clientQueue.poll(5, TimeUnit.SECONDS);
		assertNotNull("Worker should enqueue a client-thread publication", next);
		return next;
	}

	private static Filepath source(byte[] bytes) throws Exception {
		Filepath source = mock(Filepath.class);
		when(source.openInputStream()).thenAnswer(call -> new ByteArrayInputStream(bytes));
		return source;
	}
}
