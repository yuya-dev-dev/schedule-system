# Yuya 学習メモ

このファイルは、配送・設置案件スケジュール管理システムを自分で理解できるようになるための学習ガイドです。

今までCodexに任せてきた実装内容を、夏休みを使って少しずつ読める・説明できる・相談できる状態に近づけることを目的にします。

## Git操作早見表

この章は、このシステム開発に限らず、GitHub上のリポジトリへ変更を反映するときの確認用テンプレートです。

### まず現在地を確認する

作業前に、ターミナルが対象リポジトリの中にいるか確認します。

```powershell
pwd
git status --short --branch
```

確認すること:

- 表示されたフォルダが、作業したいリポジトリである
- どのブランチにいるか分かる
- 変更済みファイル、未追跡ファイルがあるか分かる

### 最新のmainを取り込む

作業前、またはmainへ直接反映する前に、GitHub上の最新mainを取り込みます。

```powershell
git switch main
git pull origin main
```

`git pull origin main` は、「GitHub上のmainの最新状態を、手元のmainへ取り込む」という意味です。

### ドキュメント変更だけをmainへ直接反映する場合

軽いドキュメント修正だけなら、PRを作らずmainへ直接反映してよい方針です。

```powershell
git switch main
git pull origin main
git status --short
git add <変更したファイル>
git commit -m "docs: 変更内容を短く書く"
git push origin main
```

例:

```powershell
git add README.md yuya.md
git commit -m "docs: update learning notes"
git push origin main
```

確認すること:

- `git add` には、今回変更したファイルだけを書く
- コミットメッセージは、何を変えたか分かる短い英語にする
- `git push origin main` を実行すると、GitHubのmainへ直接反映される

注意:

- コード変更、DB変更、設定変更、デプロイに影響する変更では、直接mainへpushしない
- 迷った場合はPRを作る

### PRを作成してmainへ反映する場合

コード変更や影響範囲が広い変更は、作業ブランチを作ってPR経由でmainへ入れます。

```powershell
git switch main
git pull origin main
git switch -c <作業ブランチ名>
```

例:

```powershell
git switch -c fix/schedule-density
```

変更後:

```powershell
git status --short
git add <変更したファイル>
git commit -m "fix: 変更内容を短く書く"
git push -u origin <作業ブランチ名>
```

例:

```powershell
git add src/main/resources/static/css/schedule.css
git commit -m "fix: adjust desktop schedule density"
git push -u origin fix/schedule-density
```

GitHub CLIでPRを作る場合:

```powershell
gh pr create --base main --head <作業ブランチ名> --title "PRタイトル" --body "PR説明"
```

例:

```powershell
gh pr create --base main --head fix/schedule-density --title "fix: adjust desktop schedule density" --body "## Summary
- adjust desktop schedule density

## Test
- not run"
```

その後、GitHub上でPRを確認し、問題なければマージします。

### すでに作業ブランチへpushしてしまった後、PRを作らずmainへ直接入れたい場合

作業ブランチの変更をmainへ取り込んで、そのままpushできます。

```powershell
git switch main
git pull origin main
git merge <作業ブランチ名>
git push origin main
```

例:

```powershell
git switch main
git pull origin main
git merge docs/update-handoff
git push origin main
```

これは、「作業ブランチのコミットを手元のmainへ取り込み、そのmainをGitHubへpushする」という意味です。

### 使い終わった作業ブランチを削除する

mainへ反映済みで不要になったブランチは、消してもよいです。

手元のブランチを削除:

```powershell
git branch -d <作業ブランチ名>
```

GitHub上のブランチを削除:

```powershell
git push origin --delete <作業ブランチ名>
```

例:

```powershell
git branch -d docs/update-handoff
git push origin --delete docs/update-handoff
```

削除は必須ではありません。残っていてもすぐに壊れるわけではありませんが、増えすぎると分かりにくくなります。

### よく見る表示の意味

`git status --short --branch` の例:

```text
## main...origin/main
 M README.md
?? 引き継ぎ.md
```

意味:

- `M`: 変更済みファイル
- `??`: Gitがまだ管理していない新規ファイル
- `main...origin/main`: 手元のmainとGitHub上のmainを比較している

### 迷ったときの安全確認

何をコミットするか不安なときは、先にこれを確認します。

```powershell
git status --short
git diff --stat
```

見ること:

- 変更ファイルが今回の作業と関係あるか
- 予想外のファイルが混ざっていないか
- 変更量が極端に多くないか

予想外の変更がある場合は、無理に `git add .` しないで止まります。

## まず結論

Java入門とSQL入門を一通りやれば、Codexとの技術的な会話はかなりしやすくなります。

ただし、この案件は単なるJavaプログラムではなく、Java / Spring Boot / HTML / CSS / JavaScript / DB / GitHub / テストで構成されたWebアプリです。

そのため、JavaとSQLだけで終わらせるより、次の順番で学ぶとこのリポジトリを理解しやすくなります。

1. Java基礎
2. SQL / DB基礎
3. Spring Boot基礎
4. HTML / CSS / JavaScriptの最低限
5. Git / GitHub
6. テスト
7. このリポジトリのコードリーディング

## 1. Java基礎

最優先です。

この案件の中心はJavaです。まずは入門書や学習サイトで、次の内容を押さえるとコードが読みやすくなります。

- class
- method
- field
- constructor
- enum
- interface
- List / Map
- Optional
- if / for / streamの基本
- 例外処理
- 日付時刻: `LocalDate`, `LocalTime`, `YearMonth`
- テストコードの読み方

この案件で特に出てくるJavaの考え方は、次のあたりです。

- 入力値を受け取るクラス
- DBの1行を表すEntity
- 画面表示用のデータを作るService
- ControllerからServiceを呼ぶ流れ
- enumで作業種別や状態を表現する設計

## 2. SQL / DB基礎

「すっきりわかるSQL」はかなり良い選択です。

この案件では、案件データ、下書き、休み、祝日などをDBに保存しています。SQLを理解すると、「画面に出ているデータがどこから来ているか」が見えやすくなります。

優先して理解したい内容は次です。

- SELECT
- INSERT
- UPDATE
- DELETE
- WHERE
- JOIN
- ORDER BY
- 主キー
- 外部キー
- NULL
- UNIQUE制約
- トランザクション
- インデックス

この案件ではPostgreSQLも使っています。

特に重要なのは、時間重複を防ぐためにDB側の制約も使っている点です。アプリ側だけでなく、DB側でも「同じ時間帯に2件入らない」ように守っています。

参考:

- PostgreSQL Tutorial: https://www.postgresql.org/docs/current/tutorial.html

## 3. Spring Boot基礎

Javaの次に大事です。

この案件のJavaコードは、ほとんどSpring Bootの上で動いています。Javaの文法だけ分かっても、Spring Bootの役割が分からないと「どこから処理が始まっているのか」が見えにくいです。

まず理解したい用語は次です。

- Controller
- Service
- Repository
- Entity
- `@GetMapping`
- `@PostMapping`
- `@Transactional`
- `@Autowired` / コンストラクタ注入
- `application.properties`
- Maven
- `mvnw.cmd test`
- `mvnw.cmd spring-boot:run`

この案件では、だいたい次の流れで処理が動きます。

```text
ブラウザ操作
  ↓
Controller
  ↓
Service
  ↓
Repository
  ↓
DB
```

例えば、スケジュール一覧を開くときは、ControllerがURLを受け取り、Serviceが月ごとの表示データを作り、テンプレートへ渡して画面を表示します。

参考:

- Spring Boot Getting Started: https://spring.io/guides/gs/spring-boot/
- Spring MVC Web Content: https://spring.io/guides/gs/serving-web-content/

## 4. HTML / CSS / JavaScriptの最低限

この案件はWebアプリなので、画面側の最低限も知っておくとかなり楽になります。

深くやりすぎる必要はありません。まずは次だけ分かれば十分です。

- HTMLのform
- input
- select
- textarea
- button
- CSSで見た目を変える仕組み
- JavaScriptのイベント
- checkboxの表示切り替え
- fetch
- DOM

この案件でJavaScriptが関係するところは、主に次です。

- 入力フォームの自動保存
- 同行ありチェック時の入力欄表示
- 保存状態の表示
- エラー表示
- 一覧へ戻る操作

## 5. Git / GitHub

Codexが何をしているかを理解するには、Git / GitHubも重要です。

優先して覚える単語は次です。

- repository
- branch
- commit
- push
- pull
- Pull Request
- merge
- conflict
- GitHub Actions
- CI

この案件では、基本的に次の流れで開発しています。

```text
mainから作業ブランチを作る
  ↓
実装する
  ↓
テストする
  ↓
commitする
  ↓
GitHubへpushする
  ↓
Pull Requestを作る
  ↓
CIが通る
  ↓
mainへmergeする
```

Pull Requestは、mainへ取り込む前の確認場所です。

CIは、GitHub上で自動的にテストを実行して、壊れていないか確認する仕組みです。

## 6. テスト

この案件はテストをかなり厚めに作っています。

そのため、テストコードを読めるようになると、仕様理解が一気に進みます。

まず覚えたいのは次です。

- JUnit
- assertion
- MockMvc
- E2Eテスト
- Testcontainers
- PostgreSQLを使った競合テスト

この案件では、テストが単なる確認ではなく、「このシステムはこう動くべき」という仕様書の役割も持っています。

例えば次のような仕様はテストで守っています。

- 12:00〜14:00 と 14:00〜16:00 は重複扱いにしない
- 同じ時間帯に別案件を入れようとしたら競合にする
- 重複した後続入力は消さずに下書きとして残す
- 休みの日には新規入力できない
- 祝日は一覧から除外する
- 公開済み案件をコピーできる
- コピー先が重複していたら新規レコードを作らない

参考:

- Playwright Java: https://playwright.dev/java/docs/intro

## 7. このリポジトリの読み方

最初から全部読もうとしなくてよいです。

おすすめの順番は次です。

### 1. READMEを読む

まずはプロジェクトの概要、起動方法、テスト方法を確認します。

```text
README.md
```

### 2. docsを読む

仕様やロードマップを確認します。

```text
docs/requirements.md
docs/business-flow.md
docs/screen-list.md
docs/database-design.md
docs/development-roadmap.md
docs/test-policy.md
```

### 3. Controllerを読む

URLと画面の入口を確認します。

```text
src/main/java/.../schedule/
src/main/java/.../request/
```

Controllerを見ると、「どのURLでどの処理が動くか」が分かります。

### 4. Serviceを読む

業務ロジックを確認します。

この案件で一番大事なのはServiceです。

例えば次のような処理があります。

- 月間スケジュールを作る
- 入力内容を自動保存する
- 重複を判定する
- 下書きを管理する
- 休みを設定する
- 祝日を同期する
- 案件をコピーする

### 5. Entityを読む

DBに保存されるデータ構造を確認します。

ここを見ると、案件がどんな項目を持っているか分かります。

### 6. テストを読む

仕様の最終確認としてテストを読みます。

テスト名を見るだけでもかなり勉強になります。

```text
src/test/java/
```

## 夏休みのおすすめ学習順

### 第1段階: Javaに慣れる

目標:

- Javaのコードを見て、何となく処理の流れが追える
- classとmethodの違いが分かる
- enumやListが怖くなくなる

やること:

- Java入門書を1冊進める
- 小さい練習問題を書く
- この案件のEntityやenumを読む

### 第2段階: SQLに慣れる

目標:

- DBに保存されているデータをイメージできる
- SELECT文が読める
- 主キーや制約の意味が分かる

やること:

- 「すっきりわかるSQL」を進める
- SELECT / INSERT / UPDATE / DELETEを練習する
- `docs/database-design.md` を読み直す

### 第3段階: Spring Bootの流れを掴む

目標:

- Controller / Service / Repositoryの役割が分かる
- URLから処理の流れを追える

やること:

- Spring Boot公式Getting Startedを1つ写経する
- この案件のControllerを読む
- 画面操作とControllerの対応を見る

### 第4段階: この案件を読む

目標:

- 「この機能はこのクラスが担当している」と言える
- Codexに具体的な質問ができる

やること:

- スケジュール一覧表示の流れを読む
- 入力フォーム保存の流れを読む
- 下書きの流れを読む
- 休み・祝日・コピー機能の流れを読む

### 第5段階: 小さい修正を自分でやってみる

目標:

- 1行〜数行の修正を自分で理解して入れられる
- テストを実行して確認できる
- PRの意味が分かる

やること:

- 文言変更
- CSSの微調整
- テスト名を読んで仕様を説明する
- 可能なら小さいテストを1つ追加する

## Codexと技術的に会話できるようになるための質問例

学習が進んできたら、Codexには次のように聞くとよいです。

```text
このControllerからServiceまでの処理の流れを、初心者向けに説明して
```

```text
このテストが保証している仕様を日本語で説明して
```

```text
このEntityの各フィールドが画面のどの入力項目に対応しているか教えて
```

```text
この機能を修正するなら、どのクラスを読むべきか順番に教えて
```

```text
このPRの差分を、Java初心者向けにレビューして
```

## この案件で特に理解すると強いテーマ

### 1. 一覧反映条件

案件が一覧に出る条件は重要です。

基本的には、依頼者名、開始時間、終了時間が揃うと一覧に反映されます。

作業種別が未入力でも、それだけで一覧から消す必要はありません。

### 2. 下書き

入力途中や重複した入力を失わないための仕組みです。

この案件では、入力を消さないことをかなり重視しています。

### 3. 重複判定

同じ時間帯に2件入らないようにする仕組みです。

アプリ側の判定だけでなく、PostgreSQL側の制約でも守っています。

### 4. 休み

休みの日は、入力フォームを開けない状態にします。

既存案件がある日に休みを設定する場合は、案件を削除して休みセルに置き換える仕様です。

### 5. 祝日

祝日は外部カレンダー同期で一覧から除外します。

祝日に案件が入る前提は持たない方針です。

### 6. コピー

公開済み案件を別日にコピーする機能です。

コピー先で時間重複があった場合、新しいレコードは作らず、コピー内容を入力フォームに表示してエラーを出します。

## 最後に

この案件は、ポートフォリオとしてかなり良い題材です。

理由は、単なる練習アプリではなく、実際の業務課題から要件定義し、MVPを決め、テストを作り、段階的に機能追加しているからです。

JavaやSQLを勉強するときも、ただ文法を覚えるのではなく、

```text
この知識は、このシステムのどこで使われているか
```

を意識すると理解が速くなります。

まずは完璧に理解しようとしなくて大丈夫です。

「読める場所を少しずつ増やす」方針で進めるのがよいです。
