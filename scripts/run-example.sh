#!/usr/bin/env sh
set -eu

mvn -q package
java -cp target/classes dev.infrai.reports.DeveloperReportApplication
