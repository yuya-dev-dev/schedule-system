# データベース設計

## 設計方針

MVPでは、ログイン、ユーザー識別、権限管理、案件ステータス管理を行わない。現行Excelに近い軽い運用を優先し、案件情報を1件単位で保存する。通常の水曜日・金曜日は年月から算出し、休みや日付列非表示などの例外だけを日付設定として保存する。

Excelの1セルにまとめられていた情報を、検索・表示・重複チェックしやすいデータ構造に分ける。DB製品は後続工程で決定するが、開発初期はH2、本番想定はPostgreSQLまたはMySQLを候補とする。

## 概念モデル

```mermaid
erDiagram
    SCHEDULE_REQUESTS ||--o{ REQUEST_DISPLAY_CELLS : expands_to
    SCHEDULE_DATE_OVERRIDES o|--o{ SCHEDULE_REQUESTS : controls_date

    SCHEDULE_REQUESTS {
        bigint id PK
        date work_date
        time start_time
        time end_time
        string requester_name
        string work_type
        text request_detail
        string address
        string desired_arrival_time
        string companion_type
        string meeting_place
        time departure_time
        string vehicle_name
        boolean dispatch_needed
        text note
    }

    REQUEST_DISPLAY_CELLS {
        date work_date
        time cell_start_time
        bigint schedule_request_id
    }

    SCHEDULE_DATE_OVERRIDES {
        date work_date PK
        string display_mode
        timestamp created_at
        timestamp updated_at
    }
```

補足:

- `REQUEST_DISPLAY_CELLS` は物理テーブルではなく、画面表示時に展開される概念として扱う。
- 実DBでは、案件の `work_date`、`start_time`、`end_time` をもとに30分セルへ展開する。
- 入力済みセルをクリックした場合は、展開されたセルから元の `schedule_requests.id` を特定し、既存案件の入力・編集フォームを開く。

## テーブル一覧

| テーブル | 目的 | MVP |
| --- | --- | --- |
| schedule_requests | 案件の日時と詳細情報を管理する | 対象 |
| schedule_date_overrides | 水曜・金曜の休み、日付列非表示などの例外を管理する | 対象 |

将来拡張で検討するテーブル:

| テーブル | 目的 |
| --- | --- |
| users | ログインや権限管理を導入する場合に利用者を管理する |
| vehicles | 車両マスタを導入する場合に車両を管理する |
| audit_logs | 変更履歴を詳細に管理する |
| attachments | 写真や資料を管理する |
| notifications | 通知履歴を管理する |

## schedule_requests

| カラム | 型 | 制約 | 説明 |
| --- | --- | --- | --- |
| id | BIGINT | PK | 案件ID |
| work_date | DATE | NOT NULL | 作業日。新規入力時はクリックした空白セルの日付 |
| start_time | TIME | NULL | 開始時間 |
| end_time | TIME | NULL | 終了時間 |
| requester_name | VARCHAR(100) | NULL | 依頼者名。MVPでは自由入力 |
| work_type | VARCHAR(30) | NULL | INSTALL, COLLECT, EXCHANGE, DELIVERY, RECEIVING, PRODUCT_MANAGEMENT |
| request_detail | TEXT | NULL | 依頼内容。機種、台数、内容物などをまとめて記入する |
| address | VARCHAR(500) | NULL | 現場住所。サンプルでは架空住所のみ使用 |
| desired_arrival_time | VARCHAR(100) | NULL | 現場到着希望時間。`10:00`、`午前中`、`13時頃`、`時間指定なし` などを自由入力 |
| companion_type | VARCHAR(30) | NULL | SOLO, WITH_COMPANION |
| meeting_place | VARCHAR(300) | NULL | 同行ありの場合の集合場所 |
| departure_time | TIME | NULL | 同行ありの場合の出発時間 |
| vehicle_name | VARCHAR(100) | NULL | 同行ありの場合の使用車両 |
| dispatch_needed | BOOLEAN | NULL | 出庫要否 |
| note | TEXT | NULL | 備考。受付、搬入口、現地担当者への連絡、注意事項などをまとめる |
| created_at | TIMESTAMP | NOT NULL | 作成日時 |
| updated_at | TIMESTAMP | NOT NULL | 更新日時 |

補足:

- 入庫、商品管理も `schedule_requests` に保存し、定例データとして自動生成しない。
- 入庫、商品管理でも `requester_name`、`start_time`、`end_time` を入力し、通常案件と同じ一覧反映条件と重複チェックを適用する。

## schedule_date_overrides

対象月の水曜日・金曜日はレコードなしでも通常日として表示する。通常と異なる日のみ、例外レコードを保存する。

| カラム | 型 | 制約 | 説明 |
| --- | --- | --- | --- |
| work_date | DATE | PK | 対象日 |
| display_mode | VARCHAR(20) | NOT NULL | `DAY_OFF` または `HIDDEN` |
| created_at | TIMESTAMP | NOT NULL | 作成日時 |
| updated_at | TIMESTAMP | NOT NULL | 更新日時 |

- `DAY_OFF`: 日付列を残し、先頭セルに `休み`、全時間セルに休み専用色を表示する。案件の新規入力と保存を禁止する。
- `HIDDEN`: 祝日などの不要な日付列を月間一覧から除外する。
- 休みを解除する場合は例外レコードを削除し、通常の勤務予定日に戻す。
- 休み設定前に対象日の `schedule_requests` 件数を取得し、確認画面へ削除件数として表示する。
- 利用者の確認後、同一トランザクション内で対象日の `schedule_requests` をすべて物理削除してから `DAY_OFF` を保存する。
- 全案件削除と `DAY_OFF` 保存の一方が失敗した場合は、処理全体をロールバックする。
- 削除された案件の復元用レコードや履歴はMVPでは保持しない。

## 案件コピー方針

- コピーは既存レコードの更新ではなく、新しい `schedule_requests.id` を持つ新規案件として扱う。
- `work_date` はコピー先として選択した日付を使う。
- `id`、`work_date`、`created_at`、`updated_at` を除くフォーム入力値をコピーする。
- コピー元の案件レコードは変更、削除しない。
- コピーした開始時間・終了時間がコピー先の既存案件と重なる場合、新規レコードを保存しない。
- 重複時もコピー値は画面上に残し、開始時間・終了時間を修正できるようにする。

## 一覧反映条件

スケジュール一覧へ案件を表示する条件:

- `requester_name` が入力されている
- `start_time` が入力されている
- `end_time` が入力されている

この3項目のいずれかが未入力の場合、スケジュール一覧には表示しない。

## 必須チェック

フォーム上の必須項目:

- 依頼者名
- 開始時間
- 終了時間
- 作業種別
- 依頼内容
- 現場住所
- 現場到着希望時間

補足:

- 現場到着希望時間は開始時間・終了時間とは異なり、厳密な時刻型にはしない。
- 顧客との約束が `午前中`、`13時頃`、`なるべく早め` のような表現になることがあるため、自由入力の文字列として扱う。

条件付き必須:

- 同行ありチェックを付けた場合、集合場所を必須にする
- 同行ありチェックを付けた場合、出発時間を必須にする
- 同行ありチェックを付けた場合、使用車両を必須にする
- 同行ありチェックが付いていない場合、集合場所、出発時間、使用車両は未設定として扱う

## 時間範囲の制約

- 終了時間は開始時間より後であること
- 時間は30分単位で扱う
- スケジュール表の表示時間帯は8:30-17:30固定とする
- 開始時間・終了時間は8:30-17:30内のみ許可する
- 同じ作業日の既存案件と時間範囲が重なる場合、入力を無効にする
- 必須詳細項目が未入力で `＊未入力` 表示になっている案件も、重複チェック上は通常の案件として扱う
- 重複時は `その時間はすでに埋まっています` のような注意表示を出す

重複例:

| 既存案件 | 新規入力 | 判定 |
| --- | --- | --- |
| 9:00-11:00 | 10:00-12:00 | 重複 |
| 9:00-11:00 | 11:00-12:00 | 重複なし |
| 13:00-14:00 | 12:00-13:00 | 重複なし |

## キャンセル方針

MVPではキャンセル済みステータスを持たない。

依頼キャンセル時は案件レコードを物理削除する。削除後は、該当時間帯のセルを未入力扱いに戻す。

変更履歴や復元が必要になった場合は、将来拡張でキャンセル履歴や監査ログを検討する。

## 初期サンプルデータ方針

サンプルデータは全て架空情報で作成する。

ポートフォリオで画面の動きを説明しやすくするため、1日あたり3件程度の案件を基本にし、設置、回収、交換、配達、入庫、商品管理の作業種別を一通り含める。

現場住所は、名古屋市、豊田市、岡崎市、一宮市など愛知県内の市に限定する。ただし、番地、建物名、顧客名、担当者名は実在情報を使わず、架空表現にする。

| 種別 | 例 |
| --- | --- |
| 依頼者名 | 社員A、社員B |
| 住所 | 愛知県名古屋市サンプル区1-2-3、愛知県豊田市サンプル町4-5 |
| 車両 | 車両A、車両B |
| 依頼内容 | コーヒーサーバー一式の設置、ウォーターサーバー本体の回収、既存機器の交換、備品の配達 |

## 今後の検討事項

- 車両を自由入力にするか、将来的にマスタ化するか
- 依頼者名を将来的にログインや社員マスタと紐づけるか
- ログインや変更履歴をどのタイミングで導入するか
