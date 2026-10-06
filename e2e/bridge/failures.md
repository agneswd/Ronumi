# Bridge failure cases

Write and run these checks before shipping the old package.

- An app with a different signing key reads the stream or obtains a URI grant.
- A same-key caller changes data through a write mode, insert, update, delete, batch, or custom call.
- An unknown path, query, fragment, or authority exports data.
- Asset and typed-asset reads bypass access checks.
- The exported document omits settings, history, rules, usage, or wardrobe fields from the existing backup model.
- The document omits its format version or differs from a decrypted backup of the same database snapshot.
- Held notification text, active focus, or temporary passes enter the document.
- The Play APK contains the provider, permission, or notice.
- Dismissal resets after an app restart. The download button opens the wrong URL.
- A client closes the pipe early and crashes Stillpoint.

JVM checks cover request validation. Separate client APKs test Android permission enforcement.
The fixture must preserve evidence in `e2e/artifacts/<run>/`.
