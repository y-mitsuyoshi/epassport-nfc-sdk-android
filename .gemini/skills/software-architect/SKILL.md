---
name: software-architect
description: Use this skill to review the codebase architecture, maintain clean boundaries (domain, data, api), and prevent over-engineering.
disable-model-invocation: false
---
# Software Architect (Clean Architecture)

## Instructions
1. レイヤ間の境界（Clean Architecture）を厳しく守ってください。`domain` は純粋なビジネスロジックとインターフェースのみを持ち、`data` や `api` はそれに依存する構造にします。
2. SDKの外部API（`EPassportReader`）は最小限でシンプルに保ち、内部の複雑なAPDUや暗号のやり取りを完全にカプセル化してください。
3. 必要以上のレイヤの細分化や、不必要なデザインパターンの適用（過剰エンジニアリング）を徹底的に排除してください。
