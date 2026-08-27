# Java可読性リファクタリング計画

## 1. 目的

このリファクタリングは、画面操作からDB保存までの処理を少数のファイルで順番に追える状態を作ることを目的とする。

目標は、単純にクラス数や行数を減らすことではない。次を可読性の判断基準とする。

- クラス名とメソッド名から役割を予測できる
- ControllerからService、Entity、Repositoryへの処理を迷わず追える
- 同じ業務ルールが複数箇所へ重複していない
- 長い処理は、意図を表す名前を持つ小さな処理へ分かれている
- booleanや多数の位置引数を見ただけで意味を推測する必要がない
- 技術的な都合だけの抽象化や、読むファイル数を増やす過剰な分割がない
- テストが業務仕様の具体例として読める

## 2. 対象と非対象

対象は `src/main/java` の58ファイルと `src/test/java` の26ファイルである。監査時点の規模は、本番Javaコード約2,900行、テストJavaコード約3,600行である。

今回のリファクタリングでは、次を変更しない。

- URL、HTTPメソッド、画面遷移
- 入力項目、必須条件、自動保存のタイミング
- 下書き、時間重複、楽観ロック、固定予定、休みの業務仕様
- DBテーブル、列、制約、Flyway Migration
- Render、Neon、環境変数、バックアップ運用
- 共通パスワードゲート、CSRFを含むセキュリティ仕様
- HTML、CSS、JavaScriptの画面仕様
- `AutosaveResult`のJSONフィールド名、Status名、利用者向けメッセージ
- Spring Boot、Java、ライブラリのバージョン

機能変更やセキュリティ改善が必要になった場合は、リファクタリングとは別のタスクとPRで扱う。

## 3. 現状の良い点

次の設計は理解しやすさと安全性に寄与しているため、基本的に維持する。

- `request`、`schedule`、`holiday`、`retention`、`config`の機能別パッケージ
- コンストラクタインジェクション
- `Clock`を使った日本時間判定とテスト時刻の固定
- `record`によるResult、View、入力値の表現
- `ScheduleRequest`に置かれた必須判定、状態遷移、入力正規化
- H2、PostgreSQL Testcontainers、MockMvc、Playwrightによる多層テスト
- PostgreSQL排他制約と楽観ロックを守るテスト

全面的なレイヤー再編や、Clean Architectureへの置き換えは行わない。現在の機能別構成を保ちながら、処理経路を短くする。

## 4. 監査結果

### 優先度1: 実際の保存経路と旧保存経路が併存している

画面の保存処理は `ScheduleRequestController` から `ScheduleRequestAutosaveService` を呼ぶ。一方で、次の旧経路が残っている。

- `ScheduleRequestPublishingService` は主にテストから使用される
- `ScheduleRequestEditingService` は本番コードとテストのどちらからも使用されていない
- `PublishCommand`、`PublishResult`、`EditResult` は旧経路のために存在する
- `ScheduleRequestForm.toCommand()` は使用されていない
- 一部の競合テストが、実際の画面とは異なる `ScheduleRequestPublishingService` を検証している

旧保存経路が併存しているため、どの保存Serviceが現在の正規経路かコードから判断しにくい。競合テストを実際の自動保存経路へ移し、不要になった旧Serviceと型を削除することを最優先とする。

ただし、同時登録とPostgreSQL排他制約は高リスク領域である。旧経路を削除する前に、同等のケースが `ScheduleRequestAutosaveService` 経由で保護されていることをテストで確認する。

### 優先度1: 時間重複処理の表現が重複している

時間重複の検索、DB例外判定、競合メッセージ生成が次へ分散している。

- `ScheduleRequestAutosaveService`
- `ScheduleRequestPublishingService`
- `ScheduleRequestEditingService`
- `RequestCopyService`
- `RecurringFixedRequestService`

Repositoryの自動生成メソッド名も非常に長く、引数の順序を読み取りにくい。旧保存経路の削除後、Repositoryへ意味の分かる短い検索メソッドを定義し、残った利用箇所で共有する。

DB例外処理は、PostgreSQLの同時登録を守るために必要である。一般化した例外フレームワークは作らず、残った保存経路で必要な範囲だけを名前付きメソッドへまとめる。

### 優先度1: 月間一覧の生成意図が位置引数から読めない

`MonthScheduleService` は次を1クラスで担当している。

- 対象月の解析
- 月タブ生成
- 水曜・金曜と祝日の判定
- 休み情報の取得
- 時間行とセルの生成
- 案件の重なり判定
- 案件色の割り当て
- URLと時刻表示の生成

さらに `ScheduleCellView` は10個の位置引数を持ち、複数のbooleanが並ぶ。`new ScheduleCellView(null, false, false, ...)` からセルの意味を判断することは難しい。

`ScheduleCellView` に「空きセル」「案件セル」「休みセル」を表す名前付きファクトリを用意する。`MonthScheduleService` は、月全体の調整とセルグリッド生成の2つまでに分ける。細かなクラスを大量に作らず、一覧生成を追うファイル数は最大2〜3個に抑える。

### 優先度1: テストが現在の処理経路と一致しない箇所がある

`ScheduleRequestWorkflowTest`、`PostgreSqlConcurrencyTest`、`ScheduleVerticalSliceTest`の一部は、画面が使わない旧PublishingServiceをfixture作成や検証に使用している。

リファクタリング後は、業務仕様を示すテストが実際のServiceを通るようにする。DB制約そのものを直接検証するテストは、Serviceテストと目的を分けて残す。

### 優先度2: 入力値の位置引数が多い

`ScheduleRequestInput` は14項目を持つ。フォーム変換では許容できるが、コピー、デモデータ、単体テストで長いコンストラクタが繰り返され、項目の意味と順序を確認しにくい。

本番コードでは、コピー用とEntity復元用の名前付き変換を用意する。テストコードでは、既定値を持つ小さなfixture builderを用意し、各テストが重要な入力だけを上書きできるようにする。Lombokや外部Builderライブラリは追加しない。

### 優先度2: `ScheduleRequest`の長い更新処理

`ScheduleRequest`は業務ルールの中心として妥当な位置にあるが、`applyInput()`には次が連続している。

- 時刻検証
- 共通項目の設定
- 入庫・商品管理の詳細消去
- 通常案件の詳細設定
- 同行条件による値の消去
- 下書き状態への遷移

Entityを複数の値オブジェクトへ全面分割すると、処理を追う際のファイル間移動が増える。Entityは維持し、`applyCommonFields`、`applyNormalWorkDetails`、`clearNormalWorkDetails`など、業務上のまとまりを表すprivateメソッドへ整理する。

### 優先度2: 日付・時間・画面遷移の規則が重複している

次の知識が複数クラスへ重複している。

- 水曜日・金曜日の判定
- 8:30〜17:30と30分単位
- 入庫・商品管理の判定
- `yyyy年M月d日（E）` の表示
- `/schedule?month=...` の生成
- 年・月入力の解析

共有対象は、3箇所以上で同じ意味を持つものに限定する。候補は次である。

- `ScheduleDatePolicy`: 登録可能日と水曜・金曜判定
- `ScheduleTimeSlots`: 営業時間、30分刻み、選択肢生成
- `ScheduleRequest.isInternalWork()`: 入庫・商品管理判定
- 月入力の解析とスケジュールURL生成: Web層の小さな共通部品

単に2行が似ているだけの処理は、無理に共通化しない。

### 優先度2: Controllerの依存先と補助処理が多い

`ScheduleRequestController`は6依存を持ち、公開案件の検索をRepositoryから直接行う。`RequestCopyController`と`ScheduleController`は月入力の解析をそれぞれ持つ。`ScheduleDayOffController`の汎用的な `Operation<T>` は、単純な例外変換を追いにくくしている。

Controllerは、入力受付、Service呼び出し、画面遷移へ限定する。共通例外処理を大規模導入せず、既存の `ScheduleRequestNotFoundException` と小さなWeb補助クラスで依存と重複を減らす。

### 優先度2: 下書き一覧取得に物理削除の副作用が隠れている

`DraftManagementService.activeDrafts()`は、一覧を返す前に過去の下書きを物理削除する。既存仕様として必要だが、メソッド名だけでは削除が起きると分からない。

挙動は維持し、Controllerから見ても「期限切れ下書きの削除と有効下書きの取得」が分かる名前または処理構造へ整理する。既存のVerticalSliceテストは過去下書きの削除を確認しているが、Service単体の `DraftManagementServiceTest` には境界日の削除ケースがないため追加する。

### 優先度2: テストクラスが大きく、fixtureが重複している

- `ScheduleVerticalSliceTest` は約900行で、登録、編集、下書き、休み、コピー、一覧表示を1クラスに含む
- 2つのブラウザE2EクラスでPlaywrightの起動、Context生成、終了処理が重複する
- Entityや入力値の生成に長いコンストラクタが繰り返される

テストを機能別に分割し、共通fixtureは用途が明確なテスト専用クラスへまとめる。継承階層を深くせず、テスト本文を読めば準備条件と期待結果が分かる状態を優先する。

### 優先度3: 周辺機能は局所的な整理で十分である

`holiday`、`retention`、`config`は責務が比較的明確である。大きな構造変更は不要で、次の局所整理だけを候補とする。

- `SecurityConfiguration`の有効・無効設定を名前付きprivateメソッドへ分ける
- `DemoDataConfiguration`の長い入力生成を名前付きfactoryへ整理する
- 起動時処理の命名を、実行条件と副作用が分かる形へ揃える
- Repositoryフィールド名を `repository` ではなく対象が分かる名前へ統一する

セキュリティ方式、祝日CSV処理、保持期間の削除条件は変更しない。

## 5. 目標とする読み順

主要機能は、最終的に次の順序で読める状態を目指す。

### 月間一覧

1. `ScheduleController`
2. `MonthScheduleService`
3. 一覧セル生成を担当する1クラス
4. `ScheduleRequestRepository`

### 案件入力と自動保存

1. `ScheduleRequestController`
2. `ScheduleRequestAutosaveService`
3. `ScheduleRequest`
4. `ScheduleRequestRepository`

### コピー、削除、休み

1. 対応Controller
2. 対応Service
3. `ScheduleDatePolicy`
4. 対応RepositoryまたはEntity

読解に不要な旧Service、未使用メソッド、重複したCommandとResultを経路から除外する。

## 6. 実装クラスタ

### クラスタ2: 中心業務のリファクタリング

#### 2A. 保存経路の一本化

- PostgreSQL同時登録テストをAutosaveService経由へ移す
- WorkflowテストとVerticalSliceのfixtureを現行経路へ移す
- 未使用のEditingServiceを削除する
- PublishingService、PublishCommand、PublishResult、EditResultを削除する
- `ScheduleRequestForm.toCommand()`を削除する
- `docs/architecture.md`の保存・編集経路を実コードへ同期する
- PostgreSQL同時登録、隣接時間、競合下書き、楽観ロックを再確認する

これは最もリスクが高いため、独立したPRにする。

旧テストは次の対応を先に作り、変更前コードで成功することを確認してから旧Serviceと旧テストを削除する。

| 旧経路で守っている動作 | Autosave経路で固定する条件 |
| --- | --- |
| 新規の同時登録 | 先着だけが公開され、敗者の入力が新しい競合下書きとして残る |
| 既存下書きの公開 | 同じIDと正しいversionを渡すと、そのレコードが公開される |
| 既存下書きの再競合 | 新規レコードを増やさず、同じIDの下書きが時間重複として残る |
| 古い画面からの更新 | version不一致を返し、最新内容を上書きしない |
| 隣接する時間帯 | 重複扱いせず両方を公開する |
| DB制約への直接違反 | PostgreSQL排他制約が重複した公開レコードを拒否する |

#### 2B. 案件モデルと保存処理の単純化

- `ScheduleRequest.applyInput()`を業務上のまとまりで分割する
- 入庫・商品管理の判定をEntityの既存メソッドへ統一する
- 重複検索に短く意味の分かるRepositoryメソッドを用意する
- AutosaveServiceの新規、更新、競合、同時競合の流れを名前で追えるようにする
- privateな `SaveResult` と内部メソッドの名前を、保存結果と処理順が分かる形へ整理する
- `AutosaveResult`のJSONフィールド、Status名、利用者向けメッセージは変更しない
- `/requests/autosave`の既存JSON契約テストと自動保存E2Eで外部契約を固定する

#### 2C. 案件画面、コピー、削除の単純化

- ScheduleRequestControllerのRepository直接参照を解消する
- フォーム画面構築の判定を名前付きメソッドへ整理する
- コピー入力の生成を名前付き変換へ移す
- 月入力解析、日付表示、スケジュールURLの重複をWeb層で整理する
- DayOffControllerの汎用Operationラッパーを単純化する
- DraftManagementServiceの期限切れ削除を、名前から副作用が分かる構造へ整理する
- 昨日以前の下書きを削除し、今日と未来の下書きを残すServiceテストを追加する

#### 2D. 月間一覧生成の単純化

- `ScheduleCellView`へ空き、案件、休みの名前付きfactoryを追加する
- MonthScheduleServiceからセルグリッド生成だけを分離する
- 案件色、継続セル、読み取り専用、休みセルの処理順を明確にする
- 入庫・商品管理の色判定を既存の業務メソッドへ統一する

### クラスタ3: 周辺機能と全体統一

#### 3A. 日付・時間規則の統一

- `ScheduleDatePolicy.isScheduleWeekday()`へ、過去・祝日・休みに依存しない水曜・金曜判定を集約する
- `isRegistrable()`は、純粋な曜日判定に過去日、祝日、休みの条件を加える登録可否判定として維持する
- 月間一覧、休み設定、固定予定、デモデータが、用途に応じて曜日判定と登録可否判定を使い分ける
- 営業時間と30分刻みを小さなScheduleTimeSlotsへ集約する
- ScheduleRequest、RequestFormPageBuilder、MonthScheduleService間の定数重複を解消する
- 固定予定、休み、デモデータの曜日判定を同じ規則へ揃える

日付規則は用途ごとに次のように使い分ける。

| 用途 | 使用する判定 |
| --- | --- |
| 月間一覧の日付列 | 曜日判定と祝日除外 |
| 休み設定 | 未来日判定、曜日判定、祝日除外 |
| 固定予定の生成 | `isRegistrable()` |
| 削除対象が固定予定かの識別 | 過去・祝日・休みに依存しない曜日判定 |
| デモデータの日付候補 | 過去・祝日・休みに依存しない曜日判定 |

#### 3B. 祝日、保持期間、設定クラスの局所整理

- 現在の責務を維持したまま長い処理と条件分岐へ名前を付ける
- 起動時副作用のクラス名とメソッド名を揃える
- Repository名と設定値の命名を統一する
- 外部CSV、削除条件、セキュリティ挙動は変更しない

#### 3C. テストコードの可読性統一

- VerticalSliceテストを案件、一覧、休み、コピーへ分割する
- Playwrightの起動と終了だけを明示的なテスト補助へまとめる
- ScheduleRequestInput用のテストfixture builderを追加する
- 重複する保存済み案件生成をテスト専用factoryへまとめる
- テスト名、Given、操作、期待結果の並びを統一する

テスト整理は一括で先に行わず、対応する本番コードが安定した後に実施する。

### クラスタ4: 最終検証と文書整理

- Java全体の差分と現在のクラス構成を最終確認する
- 旧経路、未使用クラス、不要な抽象化、分割しすぎが残っていないか確認する
- 主要画面の処理経路と関連文書のクラス名を実装へ合わせる
- 主要処理の起点となるクラスと処理経路をまとめる
- 月間表示、自動保存、コピー、削除、休み、起動時処理の読む順番を説明する
- 主要画面を確認する際の代表操作を手動確認項目として整理する
- PostgreSQLとPlaywrightを含む全テストをクリーン状態で実行する

クラスタ4は新機能を追加する工程ではない。外部仕様を維持したまま、リファクタリング結果が読みやすく、文書と一致し、既存テストで保護されている状態を最終確認する。

最終監査では、旧保存経路のクラス群が削除済みであることを確認した。クラスタ3で追加したテスト補助は、VerticalSliceテストの継承1段、テスト入力builder、保存済み案件factory、Playwrightの起動終了補助に限定されており、業務コードの理解を妨げる分割ではない。

参照が残っていなかった旧更新メソッド`ScheduleRequest.updatePublished()`は削除する。Controllerのエンドポイント、Thymeleafから参照するgetter、JPA Entityの構築に必要なメソッドは、Javaコード上の直接参照だけで未使用と判断しない。

## 7. PR分割

| PR | 主な目的 | 主な確認 |
| --- | --- | --- |
| 1 | 保存経路の一本化 | Autosave、Workflow、PostgreSQL競合、VerticalSlice |
| 2 | Entity、入力値、Autosaveの単純化 | Entity単体、Autosave、PostgreSQL競合 |
| 3 | 案件Controller、フォーム、コピー、削除 | VerticalSlice、関連E2E |
| 4 | 月間一覧とCellViewの単純化 | MonthScheduleService、VerticalSlice、一覧E2E |
| 5 | 日付・時間規則の統一 | DatePolicy、Entity、固定予定、休み、一覧 |
| 6 | 祝日、保持期間、設定の局所整理 | 各対象テスト、Cloud profileテスト |
| 7 | テスト構成と最終命名の統一 | 全テスト |
| 8 | 最終監査とJavaコード処理ガイド | 全テスト、文書と実装の照合 |

各PRは直前のPRがmainへマージされた後、最新mainから作成する。複数PRを1本の長期ブランチに積み上げない。

## 8. 各PRの完了条件

- 外部仕様、DB構造、画面挙動を変更していない
- 変更前後で守る業務ルールをPR本文に記載している
- 変更に直接対応する対象テストが成功している
- PR前にMaven testフェーズが成功している
- PostgreSQL競合を触った場合はTestcontainersの競合テストが成功している
- Controllerまたは画面データを触った場合は対応するE2EまたはVerticalSliceが成功している
- 差分に目的外の整形、名前変更、ファイル移動が混ざっていない
- レビューで不要な抽象化、挙動変更、テスト不足を確認している
- PR本文に、変更した主要クラスと処理順を記載している
- 関連する構成文書が、変更後のクラス名と処理経路に一致している

## 9. 実装上の制約

- 新しい本番依存関係を追加しない
- Lombokを追加しない
- interfaceは複数実装または明確な外部境界がある場合だけ使う
- Builderは長い入力を読むために必要な箇所へ限定する
- 1回しか使わない短い処理を、見た目だけのために別クラスへ移さない
- privateメソッド分割は、処理内容を説明できる名前が付く場合だけ行う
- コメントは実装内容ではなく、DB競合など理由がコードだけでは分からない箇所へ限定する
- Java識別子は既存どおり英語を使用し、利用者向け文言は日本語を維持する

## 10. 主なリスクと対策

### 同時登録を壊すリスク

保存経路の統合時に、PostgreSQL排他制約違反後の競合下書き保存を失う可能性がある。旧経路を削除する前に、AutosaveServiceで同時登録、隣接時間、直接DB制約をそれぞれ確認する。

`PostgreSqlConcurrencyTest`のコンテナは監査時点でPostgreSQL 17を使用している。本番NeonのPostgreSQL 18との整合は可読性変更へ混ぜず、クラスタ2開始前の独立タスクで判断する。

### テストも同時に書き換えて誤りを隠すリスク

本番コードと期待値を同じPRで大幅に書き換えない。最初に現行経路を守るテストへ移し、その後に本番コードを整理する。

### 分割しすぎて読みにくくなるリスク

クラス数の削減や増加を目標にしない。1つの機能を理解するために開くファイル数と、各クラスの責務の明確さで判断する。

### 実運用中の挙動を変えるリスク

リファクタリング中はDB Migration、認証、HTML、JavaScriptを触らない。各PRを小さくし、CI成功後に1本ずつmainへ取り込む。

## 11. クラスタ1の完了条件

- 全Javaパッケージと主要テストの責務を監査している
- 可読性上の問題を優先度付きで整理している
- 現状の良い設計と、変更しない範囲を明記している
- クラスタ2・3を独立したPRへ分割している
- 本文書がmainへ反映されている
