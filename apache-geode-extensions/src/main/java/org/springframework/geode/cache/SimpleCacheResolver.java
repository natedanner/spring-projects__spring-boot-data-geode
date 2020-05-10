/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.springframework.geode.cache;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.CacheFactory;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.internal.cache.GemFireCacheImpl;

/**
 * The {@link SimpleCacheResolver} abstract class contains utility functions for resolving GemFire/Geode
 * {@link GemFireCache} instances, such as a {@link ClientCache} or a {@literal peer} {@link Cache}.
 *
 * @author John Blum
 * @see org.apache.geode.cache.Cache
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.client.ClientCache
 * @since 1.3.0
 */
@SuppressWarnings("unused")
public abstract class SimpleCacheResolver {

	private static final AtomicReference<SimpleCacheResolver> instance = new AtomicReference<>(null);

	/**
	 * Lazily constructs and gets an instance to the {@link SimpleCacheResolver}, as needed.
	 *
	 * @return an instance of the {@link SimpleCacheResolver}.
	 * @see #newSimpleCacheResolver()
	 */
	public static SimpleCacheResolver getInstance() {

		return instance.updateAndGet(cacheResolver -> cacheResolver != null
			? cacheResolver
			: newSimpleCacheResolver());
	}

	// TODO Consider resolving the SimpleCacheResolver instance using Java's ServiceProvider API.
	private static SimpleCacheResolver newSimpleCacheResolver() {
		return new SimpleCacheResolver() { };
	}

	/**
	 * 	The 1st {@code resolve():Optional<? extends GemFire>} method signature avoids the cast
	 * 	  and the @SuppressWarnings("unchecked") annotation, but puts the burden on the caller.
	 * 	The 2nd {@code resolve():Optional<T extends GemFireCache>} method signature requires a cast
	 * 	  and the @SuppressWarnings("unchecked") annotation, but avoids putting the burden on the caller.
	 */
	private static void testCallResolve() {
		Optional<ClientCache> clientCache = getInstance().resolve();
	}

	/**
	 * The resolution algorithm first tries to resolve an {@link Optional} {@link ClientCache} instance
	 * then a {@literal peer} {@link Cache} instance if a {@link ClientCache} is not present.
	 *
	 * If neither a {@link ClientCache} or {@literal peer} {@link Cache} is available, then {@link Optional#empty()}
	 * is returned.  No {@link Throwable Exception} is thrown.
	 *
	 * @param <T> {@link Class subclass} of {@link GemFireCache}.
	 * @return a {@link ClientCache} or then a {@literal peer} {@link Cache} instance if present.
	 * @see org.apache.geode.cache.client.ClientCache
	 * @see org.apache.geode.cache.Cache
	 * @see java.util.Optional
	 * @see #resolveClientCache()
	 * @see #resolvePeerCache()
	 */
	//public static Optional<? extends GemFireCache> resolve() {
	@SuppressWarnings("unchecked")
	public <T extends GemFireCache> Optional<T> resolve() {

		Optional<ClientCache> clientCache = resolveClientCache();

		return (Optional<T>) (clientCache.isPresent() ? clientCache : resolvePeerCache());
	}

	/**
	 * Attempts to resolve an {@link Optional} {@link ClientCache} instance.
	 *
	 * @return an {@link Optional} {@link ClientCache} instance.
	 * @see org.apache.geode.cache.client.ClientCacheFactory#getAnyInstance()
	 * @see org.apache.geode.cache.client.ClientCache
	 * @see java.util.Optional
	 * @see #isClient(ClientCache)
	 */
	public Optional<ClientCache> resolveClientCache() {

		try {
			return Optional.ofNullable(ClientCacheFactory.getAnyInstance())
				.filter(this::isClient);
		}
		catch (Throwable ignore) {
			return Optional.empty();
		}
	}

	/**
	 * Determine whether the given cache instance is truly a {@link ClientCache}.
	 *
	 * The problem is, {@link GemFireCacheImpl} implements both the (peer) {@link Cache}
	 * and {@link ClientCache} interfaces. #sigh
	 *
	 * @param clientCache {@link ClientCache} instance to evaluate.
	 * @return a boolean value indicating whether the cache instance is truly a {@link ClientCache}.
	 * @see org.apache.geode.cache.client.ClientCache
	 */
	private boolean isClient(ClientCache clientCache) {
		return !(clientCache instanceof GemFireCacheImpl) || ((GemFireCacheImpl) clientCache).isClient();
	}

	/**
	 * Attempts to resolve an {@link Optional} {@link Cache} instance.
	 *
	 * @return an {@link Optional} {@link Cache} instance.
	 * @see org.apache.geode.cache.CacheFactory#getAnyInstance()
	 * @see org.apache.geode.cache.Cache
	 * @see java.util.Optional
	 * @see #isPeer(Cache)
	 */
	public Optional<Cache> resolvePeerCache() {

		try {
			return Optional.ofNullable(CacheFactory.getAnyInstance())
				.filter(this::isPeer);
		}
		catch (Throwable ignore) {
			return Optional.empty();
		}
	}

	/**
	 * Determine whether the given cache instance is truly a {@literal peer} {@link Cache}.
	 *
	 * The problem is, {@link GemFireCacheImpl} implements both the (peer) {@link Cache}
	 * and {@link ClientCache} interfaces. #sigh
	 *
	 * @param cache {@link Cache} instance to evaluate.
	 * @return a boolean value indicating whether the cache instance is truly a {@literal peer} {@link Cache}.
	 * @see org.apache.geode.cache.Cache
	 */
	private boolean isPeer(Cache cache) {
		return !(cache instanceof GemFireCacheImpl) || !((GemFireCacheImpl) cache).isClient();
	}

	/**
	 * Requires an instance of either a {@link ClientCache} or a {@literal peer} {@link Cache}.
	 *
	 * @param <T> {@link Class subclass} of {@link GemFireCache} to resolve.
	 * @return an instance of either a {@link ClientCache} or a {@literal peer} {@link Cache}.
	 * @see org.apache.geode.cache.client.ClientCache
	 * @see org.apache.geode.cache.Cache
	 * @see org.apache.geode.cache.GemFireCache
	 * @see #resolve()
	 */
	public <T extends GemFireCache> T require() {
		return this.<T>resolve()
			.orElseThrow(() -> new IllegalStateException("GemFireCache not found"));
	}
}
