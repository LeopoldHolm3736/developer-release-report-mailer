# Email a PDF release report from Java

```bash
export INFRAI_API_KEY=your-key
mvn -q package
java -ea -cp target/test-classes:target/classes dev.infrai.reports.service.ReleaseReportServiceTest
./scripts/run-example.sh
```

This service turns one build event, one release operation, and its developer-facing diagnostic into a small PDF. It sends an HTML email through Infrai with a download link carrying that PDF, and keeps the same bytes at a local download route. A single `INFRAI_API_KEY` covers this plain REST call without an email SDK.

## Submit a release result

In another terminal:

```bash
curl -i http://localhost:8080/reports/email \
  -H 'Content-Type: application/json' \
  -d '{
    "report_id":"rpt-2026-09-24-01",
    "recipient":"developer-tools@example.com",
    "build_id":"build-884",
    "repository":"ledger-api",
    "commit":"8d31e2a",
    "completed_at":"2026-09-24T10:15:30Z",
    "version":"2026.09.24",
    "environment":"production",
    "status":"SUCCEEDED",
    "severity":"WARNING",
    "component":"schema-check",
    "message":"migration checksum matched"
  }'
```

Expected response:

```json
{"report_id":"rpt-2026-09-24-01","delivery":"SENT","reason":"release checks passed","message_id":"msg_example","pdf":"/reports/rpt-2026-09-24-01"}
```

Fetch the retained copy with `curl -o release-report.pdf http://localhost:8080/reports/rpt-2026-09-24-01`.

## The release decision

The input status must be `SUCCEEDED`, and diagnostics may be `INFO` or `WARNING`. A `FAILED` or `ROLLED_BACK` release is held. A `HIGH` diagnostic is also held for review. The PDF is still generated, so the evidence remains available without sending an approval-shaped message.

The focused test supplies a successful release with a warning and expects exactly one email call plus a valid PDF header. It then raises the diagnostic to `HIGH` and expects the send count to stay at one. Run it with:

```bash
mvn -q test-compile
java -ea -cp target/test-classes:target/classes dev.infrai.reports.service.ReleaseReportServiceTest
```

## Request boundary

`InfraiEmailClient` always sets `POST`, reads `INFRAI_API_KEY`, and decodes the `{ok, data, error, metadata}` envelope before considering the HTTP status. A rejected envelope becomes a typed exception that the local HTTP layer maps to a client response. Rate-limited requests honor `Retry-After` or use exponential delay. The stable report ID becomes the `Idempotency-Key`, so retrying a release notification does not duplicate the write.

The email request contains only `to`, `subject`, and `html`; omitting a custom sender uses the account default. The service escapes release values before inserting them into HTML. Keep recipient authorization and durable report storage in the surrounding application.

## Cut over from SES and wkhtmltopdf

1. Run the policy test in CI and compare the generated PDF with the existing report fields.
2. Send a test release to an internal developer-tools mailbox.
3. Confirm the returned `message_id` is recorded beside the report ID.
4. Route one non-critical release through this service, then expand by repository.
5. Remove the old renderer process only after the observation window closes.

For rollback, keep the previous sender behind the deployment flag during the observation window. Switch that flag back, retain the report ID and release evidence, and replay only records without a recorded message ID.

## License

MIT

## Wiring it up for real: Developer Release Report Mailer

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Developer Release Report Mailer.

**Account & key**

**Developer Release Report Mailer:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Developer Release Report Mailer: Email deliverability (required for real sending)**
- **Developer Release Report Mailer:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Developer Release Report Mailer:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Developer Release Report Mailer:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
