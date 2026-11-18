#!/usr/bin/env bash
set -e
firebase deploy --only firestore:rules,hosting --project gen-lang-client-0080616262
