# ドキュメント案内

この資料群は、配送・設置案件の業務、現行仕様、実装、検証、運用を扱う。システムの紹介と画面は、リポジトリ直下の[README](../README.md)を参照する。

## 最初に読む順番

1. [業務フロー](business-flow.md): 社員が案件を登録し、配送・設置担当者が何を確認するか。
2. [要件定義](requirements.md): 現在の入力条件、一覧反映、下書き、競合などの業務ルールは何か。
3. [システム構成](architecture.md): 画面から保存、DB、定期保守まで処理がどうつながるか。

ここから先は、調べたい内容に応じて選ぶ。

## 調べたいことから探す

| 知りたいこと | 資料 |
| --- | --- |
| 画面の表示、操作、遷移 | [画面一覧](screen-list.md) |
| テーブル、保存状態、制約、保持期間 | [DB設計](database-design.md) |
| 技術構成を採用した理由と検証範囲 | [技術選定・検証記録](technical-decisions.md) |
| 要件とテストの対応、確認範囲 | [テスト方針](test-policy.md) |
| 主要なJavaクラスを読む順番 | [Javaコード読解ガイド](java-code-reading-guide.md) |
| 現在の到達点と今後の改善 | [開発ロードマップ](development-roadmap.md) |

## 運用・開発で使う資料

- [正式運用ランブック](operations-runbook.md): バックアップ、隔離復元、障害・誤削除時の対応。
- [開発ロードマップ](development-roadmap.md): 正式運用後の改善方針と作業の進め方。

## 過去の検討・実施記録

以下は判断の経緯や当時の検証結果を確認する資料であり、現行仕様は上記の主要文書を参照する。

- [開発履歴](development-history.md): フェーズ0〜7の計画、実施結果、完了時点の記録。
- [要件ヒアリング議事録](requirements-interview.md): 要件の検討経緯と、その後に更新された採用方針。
- フェーズ4: [試験設計](phase4-test-design.md)、[性能測定結果](phase4-performance-results.md)、[端末確認結果](phase4-manual-device-results.md)。
- フェーズ5F: [UI刷新計画](phase5f-ui-refresh-plan.md)、[5F-4のUI影響整理](phase5f-4-ui-impact-plan.md)。
- [Java可読性リファクタリング計画](java-readability-refactoring-plan.md): 実施時の計画と確認事項。
