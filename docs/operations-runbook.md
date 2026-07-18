# 限定運用ランブック

## 対象と前提

この手順は、Render Free Web ServiceとNeon Free PostgreSQLを使う5人規模の限定運用を対象とする。共通パスワード、Neon接続情報、バックアップ設定ファイル、バックアップ本体はGit、README、会話、スクリーンショットへ保存しない。

Neon Freeのinstant restoreは短時間の事故対応に使えるが、長期保持の代替にはしない。2026年7月19日時点の公式情報では、Freeプランの復元履歴は最大6時間、手動スナップショットは1件である。重要な変更前はNeon Consoleで手動スナップショットを作成し、日次の論理バックアップを別に保持する。

バックアップ保管先は、会社が承認し、アクセス制限と暗号化が有効な領域だけを使用する。リポジトリ、GitHub、Renderのローカルファイルシステム、個人用の未承認クラウドストレージは使用しない。

## 初回設定

1. Neon Consoleから、プーラーではない直接接続用のJDBC URLを確認する。
2. 会社承認済みの保管先に、設定ファイル用フォルダとバックアップ出力先フォルダを作る。
3. 次を実行する。`-ApprovedStorage` は保管先の承認を確認するため必須にしている。

```powershell
.\scripts\Initialize-PostgreSqlBackup.ps1 `
  -ConfigurationPath "D:\approved-backups\schedule-system\backup-config.clixml" `
  -OutputDirectory "D:\approved-backups\schedule-system\archives" `
  -ApprovedStorage
```

パスワードはWindowsのDPAPIで保護された設定ファイルに保存される。同じWindowsユーザー・同じPCだけが復号できる。設定ファイルもバックアップ出力先と同じアクセス制限された領域に置く。

## 日次バックアップ

営業日の業務終了後に次を実行する。スクリプトはDocker版PostgreSQLクライアントでcustom formatのdumpを作成し、SHA-256ファイルを併記する。過去14日より古い `schedule-system-*.dump` と対応するSHA-256ファイルは、成功したバックアップの後に削除する。

```powershell
.\scripts\Backup-PostgreSql.ps1 `
  -ConfigurationPath "D:\approved-backups\schedule-system\backup-config.clixml"
```

初回実行時とPostgreSQLメジャーバージョン変更時は、NeonのPostgreSQLバージョンとDockerクライアントイメージのメジャーバージョンを確認する。バックアップではNeonの直接接続用ホストを使い、プーラー接続は使わない。

## 復元確認

月1回、またはバックアップ方式・Migration変更の後に、最新バックアップを隔離コンテナへ復元する。本番Neon、Render、既存DBへ書き込む操作は行わない。

```powershell
.\scripts\Test-PostgreSqlBackup.ps1 `
  -BackupFile "D:\approved-backups\schedule-system\archives\schedule-system-YYYYMMDD-HHMMSS+0900.dump"
```

成功条件は、SHA-256一致、archive読取り、必須テーブル、Flyway失敗履歴なし、主要制約、各テーブル件数の確認である。実施日、担当者、対象バックアップ名、成功結果を社内の運用記録へ残す。バックアップ内の実在データは会話やGitへ出さない。

## 誤削除またはデータ破損

1. 利用者へ編集停止を連絡し、発生時刻と対象を記録する。
2. 6時間以内の事故はNeon Consoleのinstant restoreを使い、まず別ブランチまたはプレビューで復元時点の内容を確認する。
3. 直近の手動スナップショットがある場合も、先にプレビューで内容を確認する。
4. 長時間前の事故、またはNeon復元で解決しない場合は、最新の論理バックアップをこのランブックの隔離手順で検証する。
5. 本番への切替・復元は、ユーザーと導入判断者が対象時点と影響範囲を確認してから行う。復元前の状態を失わないよう、先に現時点のbackupまたはsnapshotを取得する。
6. 復元後は、作業日から1か月を過ぎたデータが残っていないこと、一覧・詳細・保存が動くことを確認する。

## RenderまたはNeonの障害

1. Render Dashboardで直近デプロイとログを確認し、秘密値や実在案件情報を共有しない。
2. Render障害時は、サービス復帰とNeon接続可否を確認する。無料枠のスリープ復帰は障害と区別する。
3. Neon障害時は、Neon StatusとConsoleでプロジェクト・branchの状態を確認する。
4. 接続情報、Migration、バックアップを推測で変更しない。必要なら直近バックアップの隔離復元でデータの可用性を確認する。

## 共通パスワードの変更

共通パスワードの管理と変更作業はユーザーとCodexで行う。秘密値自体は会話や文書に書かない。

1. 新しいパスワードを安全な経路で決める。
2. Renderの `SCHEDULE_ACCESS_PASSWORD` を更新し、再デプロイ後に新旧パスワードでアクセス結果を確認する。
3. 利用者へ新しい値を安全な経路で共有する。
4. 変更日時と担当者だけを運用記録へ残す。

## 6Eの運用開始条件

- 会社承認済みの保管先、日次実行担当者、運用記録の場所が決まっている。
- 本番Neonから初回バックアップを取得し、隔離復元が成功している。
- 実施日、担当者、対象バックアップ名を記録している。
- 上記が終わるまでは、6Eは「実装完了・運用開始待ち」とする。
