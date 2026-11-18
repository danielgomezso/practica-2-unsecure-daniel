import '@angular/compiler';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { build } from 'esbuild';
import { createEnvironmentInjector, runInInjectionContext } from '@angular/core';
import { HttpRequest, HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom, of, throwError } from 'rxjs';

await build({
  stdin: {
    contents: `export { authInterceptor } from './src/app/interceptors/auth.interceptor';
               export { AuthService } from './src/app/services/auth.service';`,
    resolveDir: process.cwd(), loader: 'ts'
  },
  outfile: '.test-build/interceptor.mjs', bundle: true, platform: 'node', format: 'esm',
  packages: 'external', tsconfigRaw: { compilerOptions: { experimentalDecorators: true } }
});
const { authInterceptor, AuthService } = await import('../.test-build/interceptor.mjs');
globalThis.window = { location: { origin: 'http://localhost:4200' } };

function fixture(token = 'token-de-prueba') {
  let loggedOut = false;
  let route = null;
  const injector = createEnvironmentInjector([
    { provide: AuthService, useValue: {
      obtenerToken: () => token,
      logout: () => { loggedOut = true; }
    } },
    { provide: Router, useValue: { navigate: value => { route = value; return Promise.resolve(true); } } }
  ]);
  return {
    run: (url, next) => runInInjectionContext(injector, () => authInterceptor(new HttpRequest('GET', url), next)),
    close: () => injector.destroy(),
    get loggedOut() { return loggedOut; },
    get route() { return route; }
  };
}

test('inyecta Bearer en la API y conserva la solicitud original', async () => {
  const f = fixture();
  let received;
  await firstValueFrom(f.run('http://localhost:8080/api/cuentas/2', req => {
    received = req;
    return of(new HttpResponse({ status: 200 }));
  }));
  assert.equal(received.headers.get('Authorization'), 'Bearer token-de-prueba');
  assert.equal(f.loggedOut, false);
  f.close();
});

test('sin token no agrega Authorization', async () => {
  const f = fixture(null);
  await firstValueFrom(f.run('/api/cuentas/2', req => {
    assert.equal(req.headers.has('Authorization'), false);
    return of(new HttpResponse());
  }));
  f.close();
});

test('no envia credenciales a terceros ni al login', async () => {
  const f = fixture();
  for (const url of ['https://tercero.example/api/cuentas', 'http://localhost:8080/api/auth/login']) {
    await firstValueFrom(f.run(url, req => {
      assert.equal(req.headers.has('Authorization'), false);
      return of(new HttpResponse());
    }));
  }
  f.close();
});

for (const status of [401, 403]) {
  test(`ante ${status} limpia sesion, redirige y propaga error`, async () => {
    const f = fixture();
    const error = new HttpErrorResponse({ status });
    await assert.rejects(firstValueFrom(f.run('/api/cuentas/3', () => throwError(() => error))), e => e === error);
    assert.equal(f.loggedOut, true);
    assert.deepEqual(f.route, ['/login']);
    f.close();
  });
}

test('un error 500 no cierra la sesion', async () => {
  const f = fixture();
  await assert.rejects(firstValueFrom(f.run('/api/cuentas/2', () => throwError(() => new HttpErrorResponse({ status: 500 })))));
  assert.equal(f.loggedOut, false);
  assert.equal(f.route, null);
  f.close();
});

test('AuthService obtiene el token almacenado y limpia almacenamiento y signal', () => {
  const storage = new Map();
  globalThis.localStorage = {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: key => storage.delete(key)
  };
  storage.set('insecure_pay_user', JSON.stringify({ token: 'jwt-almacenado', username: 'carlos_mora' }));
  const auth = new AuthService({});
  assert.equal(auth.obtenerToken(), 'jwt-almacenado');
  auth.logout();
  assert.equal(auth.currentUser(), null);
  assert.equal(storage.has('insecure_pay_user'), false);
  storage.set('insecure_pay_user', '{invalido');
  assert.equal(auth.obtenerToken(), null);
  assert.equal(storage.has('insecure_pay_user'), false);
});
