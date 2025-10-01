# GitHub Workflows

Security-first workflows following industry best practices for Java projects.

## Architecture

### Reusable Workflows
- **`reusable_build_test.yml`** - Build & test with configurable timeouts
- **`reusable_deploy.yml`** - Deploy with signing, attestation & conditional environments
- **`reusable_update.yml`** - Update dependencies with optional deployment

### Trigger Workflows
- **`on_push.yml`** - Test branches & PRs (no deployment)
- **`on_merge.yml`** - Deploy on PR merge with change detection
- **`on_daily.yml`** - Scheduled dependency updates with auto-deploy
- **`dependabot.yml`** - Automated dependency PRs (weekly, grouped)

## Security Patterns

### Golden Defaults
```yaml
permissions: {} # Workflow root - no permissions
jobs:
  build:
    permissions:
      contents: read # Per-job only what's needed
```

### Safe Triggers
- ✅ `pull_request` (not `pull_request_target`)
- ✅ `pull_request: types: [closed]` with merge check
- ✅ Concurrency control prevents races
- ✅ Path-based filtering ignores non-code changes

### Supply Chain Security
- **Artifact signing**: Sigstore with `sigstore/gh-action-sigstore-python@v3.0.1`
- **Build attestation**: `actions/attest-build-provenance@v1`
- **Version pinning**: Semantic versions (`@v4`) for maintainability
- **Short retention**: Artifacts expire in 1 day

### Smart Deployment
- **Change detection**: Only deploy when code changes since last release
- **Conditional environments**: Manual deployments use approval, automated don't
- **Branch protection bypass**: Optional `BOT_TOKEN` for automated pushes
- **Dual targets**: GitHub Packages (default) + Maven Central (optional)

## Configuration

### Workflow Features
```yaml
# Configurable timeouts
timeout_minutes: 15 # Default, adjustable per project

# Conditional testing
run_test: true # Can skip for deploy-only scenarios

# Environment control
use_environment: false # No approval for automation
use_environment: true  # Approval required for manual
```

### Secret Management
```yaml
secrets: inherit # Pass all secrets to reusable workflows
```

**Required secrets:**
- `GPG_SIGNING_KEY` + `GPG_PASSPHRASE` - Artifact signing
- `OSSH_USER` + `OSSH_PASS` - Maven Central (optional)
- `BOT_TOKEN` - Branch protection bypass (optional)

## Deployment Flow

### Automated (PR Merge)
1. PR merged to main → `on_merge.yml` triggered
2. Check for changes since last release
3. Build & test → Deploy to GitHub Packages
4. Create release with signed artifacts

### Automated (Daily Updates)
1. Monday 6 AM → `on_daily.yml` triggered
2. Update dependencies → Test → Commit to main
3. Auto-deploy if changes detected
4. Uses `BOT_TOKEN` to bypass branch protection

### Manual (Workflow Dispatch)
1. `on_merge.yml` manual trigger
2. Uses approval environment for governance
3. Choose GitHub Packages or Maven Central
4. Full build, test, sign, deploy cycle

## Best Practices Applied

### ✅ Security Compliance
- **Least privilege**: Root `permissions: {}`, per-job grants
- **Separate concerns**: Test workflows ≠ deploy workflows
- **Safe inputs**: No shell injection, proper quoting
- **Supply chain**: Signing + attestation + short retention

### ✅ Operational Excellence
- **DRY principle**: Reusable workflows eliminate duplication
- **Flexible timeouts**: Configurable for different project sizes
- **Smart triggers**: Deploy only when needed
- **Meaningful names**: Emojis + variables in step names

### ✅ Developer Experience
- **Automated everything**: Dependencies, testing, deployment
- **Clear permissions**: Comments explain each permission need
- **Fallback tokens**: `BOT_TOKEN || GITHUB_TOKEN` pattern
- **Unified versioning**: Date-based for conflict-free releases

## Migration from Legacy

**Removed:** SBOM generation, `pull_request_target`, SHA pinning, complex versioning
**Added:** Conditional environments, bot token support, unified secret passing
**Improved:** Security posture, maintainability, automation reliability

## Usage

**Test branch:** Push → automatic testing
**Deploy release:** Merge PR → automatic deployment
**Update deps:** Monday 6 AM → automatic updates + deployment
**Manual deploy:** Actions → Deploy → choose options + approve
