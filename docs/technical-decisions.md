# 技術選定・検証記録

## 目的

フェーズ1で決定した技術構成と、時間重複・同時操作に関する検証結果を記録する。実装中に前提が変わった場合は、理由と影響範囲をこの文書へ追記する。

## 採用構成

| 項目 | 採用内容 | 判断理由 |
| --- | --- | --- |
| Java | Java 21 LTS | 長期サポート版であり、Spring Boot 3.5系の安定した実行基盤になる |
| Webフレームワーク | Spring Boot 3.5.15 | Spring Boot 4系への早期移行を避け、学習資料とライブラリ互換性を優先する |
| ビルド | Maven Wrapper | 利用者ごとのMavenインストールを不要にし、同じバージョンで再現できる |
| 画面方式 | ThymeleafによるサーバーサイドHTML | 月間一覧と入力フォームが中心であり、初期MVPではSPAの複雑さが不要 |
| ブラウザ側処理 | 必要最小限のJavaScriptとFetch API | 動的フォームとフォーカス離脱時の自動保存だけを非同期化する |
| ORM | Spring Data JPA / Hibernate | Entity、Repository、楽観ロックをSpring標準構成で扱える |
| DBマイグレーション | Flyway | DB変更をSQLファイルとして履歴管理し、H2とPostgreSQLへ同じ順序で適用できる |
| ローカルDB | H2ファイルDB | 画面開発と通常起動を軽くし、追加サービスなしで動かせる |
| 本番想定DB | PostgreSQL | 時間範囲の排他制約をDBレベルで表現でき、同時登録を確実に防げる |
| クラウド試験環境 | Render Free Web Service + Neon Free PostgreSQL | URL共有、共通パスワードゲート、PostgreSQL保存、無料枠制約を低コストで確認できる |
| 本番相当テスト | Testcontainers PostgreSQL | 実際のPostgreSQLを一時起動し、H2との差異と競合処理を自動検証できる |
| 自動テスト | JUnit 5、AssertJ、Spring Boot Test | 単体・結合テストをSpring Boot標準の範囲で構成できる |
| CI | GitHub Actions | Pull Requestとmainへの反映時にJava 21で全テストを再実行できる |

## DBの使い分け

H2は単独でのローカル起動、デモ、高速な基本テストに限定する。アプリ層でも `PUBLISHED` の時間重複を事前確認するため、通常の連続操作ではH2でも競合入力を `DRAFT` として保持できる。ただし、同時送信の競争状態を最後に防ぐ排他制約はPostgreSQL固有である。複数人へURLを共有する試験と正式運用ではPostgreSQLを必須とし、本番相当の同時登録はTestcontainers上のPostgreSQLで検証する。

FlywayのSQLは次の場所へ分ける。

- `db/migration/common`: H2とPostgreSQLで共通のテーブル、チェック制約、索引
- `db/migration/postgresql`: PostgreSQL固有の時間範囲排他制約

## クラウド試験環境

2026年7月時点では、無料枠での試験環境としてRender Free Web ServiceとNeon Free PostgreSQLを採用している。

- Render URL: `https://schedule-system-hekm.onrender.com`
- Render側では `SPRING_PROFILES_ACTIVE=cloud` を指定する
- DB接続情報と共通パスワードはRenderの環境変数へ設定し、Git管理ファイルへ書かない
- `cloud` profileではPostgreSQLへ接続し、PostgreSQL用Flyway Migrationを適用する
- URL共有に加えて、共通パスワードゲートで最低限の入口制限をかける
- Render無料枠はスリープするため、初回アクセスやスリープ復帰時に待ち時間が発生する

Render + Neonは、社員確認済みの5人規模限定運用環境として扱う。当面は無料枠を継続し、ロード時間への改善要望が出た場合だけ有料構成を検討する。実在案件の入力前には、会社承認済み保管先への初回バックアップ取得と隔離復元を記録する。バックアップ・復元・障害時対応は `docs/operations-runbook.md` に従う。

## 自動保存方式

- 入力欄からフォーカスが離れた時点でFetch APIにより保存APIを呼ぶ。
- 前のページへ戻る操作では、画面遷移前に未送信項目の保存を試みる。
- 画面には保存中、保存済み、保存失敗を表示する。
- 通信失敗時は入力値を画面に残し、利用者が再試行できるようにする。
- 一覧反映条件を満たさない入力は `DRAFT`、満たす入力は `PUBLISHED` とする。

APIのURL、リクエスト形式、再試行回数はフェーズ2から3Aで画面実装と一緒に確定する。

## 同時実行方式

### 新規案件の時間重複

- 通常のトランザクション分離レベルはPostgreSQLの既定である `READ COMMITTED` とする。
- 公開処理は利用者の保存操作ごとに独立したトランザクションで実行する。
- `PUBLISHED` の時間範囲にはPostgreSQLのGiST排他制約を設定する。
- 時間範囲は半開区間 `[開始, 終了)` とし、12:00-14:00の後に14:00開始を許可する。
- アプリ層でも公開前に既存の `PUBLISHED` と時間が重なるか確認し、重複入力を理由と競合時間帯付きの `DRAFT` として保存する。
- 同時送信で事前確認をすり抜けた場合は、排他制約違反のSQLSTATE `23P01`、または同じ排他制約の検査中に発生したデッドロック `40P01` を時間重複へ変換し、ロールバック後の別トランザクションで後続入力を `DRAFT` として保存する。
- 利用者へは `その時間はすでに埋まっています` と表示できる結果と、再開可能な下書きIDを返す。

アプリ側の事前重複チェックはH2とPostgreSQLの通常操作をそろえ、利用者へ早く案内するために使う。最終的な同時実行時の整合性はPostgreSQLのDB制約で保証する。

### DRAFTからPUBLISHEDへの遷移

- `DRAFT` は時間枠を確保せず、重複検索の対象にしない。
- 一覧反映条件を満たした下書きを `PUBLISHED` へ移す直前に、既存案件との重複を再確認する。
- 重複がなければ同じレコードを `PUBLISHED` へ更新し、下書き理由とエラー詳細を消す。
- 重複があれば `DRAFT` の入力値を維持し、理由を `TIME_CONFLICT`、エラー詳細を競合時間帯へ更新する。

### 同一案件の同時編集

- `schedule_requests.version` をJPAの `@Version` として使う。
- 古いバージョンからの更新は上書きせず、楽観ロックエラーとして扱う。
- 画面上の入力値を残し、最新情報の再読込を促す表示はフェーズ3Bで追加する。

## 検証結果

PostgreSQL 17のTestcontainers結合テストで次を確認した。

- 同じ日の重なる時間範囲を2処理から同時に保存すると、先着1件だけが `PUBLISHED` になる。
- 後続処理は排他制約違反から時間重複結果へ変換され、入力値を持つ `DRAFT` として保存される。
- DBには先着の `PUBLISHED` 1件と後続の `DRAFT` 1件が残る。
- H2でも通常の重複入力は `DRAFT` として保持される。
- `DRAFT` は時間枠を確保せず、公開時に重複を再確認して `PUBLISHED` または理由付き `DRAFT` になる。
- 同じ案件を2つの古いコピーから更新すると、後から保存したコピーは楽観ロックで拒否される。

この検証により、H2だけでは確認できない本番相当DBの競合挙動をフェーズ1で確認できた。
