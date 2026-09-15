import { test } from "node:test";
import assert from "node:assert/strict";
import { validity, requestedExpiry, credentialStatus, recordStatus } from "../lib/validity";
const issued = 1_800_000_000_000, expiresAt = issued + 1000;
test("legacy has no assumed expiry; exact boundary expires", () => {
  assert.equal(validity({ iat: issued }, expiresAt).expiryStatus, "not-recorded");
  assert.equal(validity({ iat: issued, expiresAt }, expiresAt - 1).expiryStatus, "within-validity");
  assert.equal(validity({ iat: issued, expiresAt }, expiresAt).expiryStatus, "expired");
  assert.equal(validity({ iat: issued, expiresAt }, issued - 1).issuedInFuture, true);
});
test("issuance needs an explicit, future, valid integer date", () => {
  assert.equal(requestedExpiry(expiresAt, issued), expiresAt);
  for (const value of [undefined, null, "1800000001000", expiresAt + .5, -1, NaN, Infinity, 8_640_000_000_000_001, issued])
    assert.throws(() => requestedExpiry(value, issued));
  for (const iat of [null, "1800000000000", issued + .5, -1, NaN, Infinity, 8_640_000_000_000_001])
    assert.throws(() => validity({ iat, expiresAt }, issued));
});
test("revocation wins and unrecorded/expired dates never become active", () => {
  const expired = validity({ iat: issued, expiresAt }, expiresAt);
  assert.equal(credentialStatus(expired, issued), "revoked");
  assert.equal(credentialStatus(expired, null), "expired");
  assert.equal(credentialStatus(validity({ iat: issued }, expiresAt), null), "expiry-not-recorded");
  assert.equal(credentialStatus(validity({ iat: issued, expiresAt }, issued), null, false), "signature-only");
  assert.equal(recordStatus({ issued_at: issued, expiresAt, revoked_at: null }, issued), "active");
  assert.equal(recordStatus({ issued_at: issued, expiresAt, revoked_at: null }, expiresAt), "expired");
});
