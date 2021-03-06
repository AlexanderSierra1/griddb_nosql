/*
   Copyright (c) 2017 TOSHIBA Digital Solutions Corporation

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.toshiba.mwcloud.gs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.toshiba.mwcloud.gs.common.GSErrorCode;
import com.toshiba.mwcloud.gs.common.RowMapper;

/**
 * <div lang="ja">
 * ロウキーの合致条件を表します。
 *
 * <p>{@link GridStore#multiGet(java.util.Map)}における取得条件を
 * 構成するために使用できます。</p>
 *
 * <p>条件の種別として、範囲条件と個別条件の2つの種別があります。
 * 両方の種別の条件を共に指定することはできません。
 * 条件の内容を何も指定しない場合、対象とするすべてのロウキーに
 * 合致することを表します。</p>
 *
 * @param <K> 合致条件の評価対象とするロウキーの型
 *
 * @since 1.5
 * </div><div lang="en">
 * Represents the condition that a {@link RowKey} satisfies.
 *
 * <p>This is used as the search condition in {@link GridStore#multiGet(java.util.Map)}</p>
 *
 * <p>There are two types of conditions, range condition and individual condition.
 * The two types of conditions cannot be specified at the same time.
 * If the condition is not specified, it means that the condition is satisfied in all the target row keys.</p>
 *
 * @param <K> type of {@link RowKey}
 *
 * @since 1.5
 * </div>
 */
public class RowKeyPredicate<K> {

	private static final RowMapper.Config KEY_MAPPER_CONFIG =
			new RowMapper.Config(true, true, true, true);

	private static final Map<GSType, RowMapper> SINGLE_MAPPER_MAP =
			makeSingleMapperMap();

	private static final Map<GSType, Class<?>> SINGLE_CLASS_MAP =
			makeSingleClassMap();

	private static final Map<Class<?>, GSType> SINGLE_TYPE_MAP =
			makeSingleTypeMap(SINGLE_CLASS_MAP);

	private static boolean stringRangeRestricted = false;

	private final Class<K> bindingClass;

	private final Class<?> keyClass;

	private final RowMapper mapper;

	private final boolean rangeAcceptable;

	private K start;

	private K finish;

	private Set<K> distinctKeys;

	private RowKeyPredicate(
			Class<K> bindingClass, Class<?> keyClass, RowMapper mapper)
			throws GSException {
		checkMapper(mapper);
		this.bindingClass = bindingClass;
		this.keyClass = keyClass;
		this.mapper = mapper;
		this.rangeAcceptable = isRangeKeyAcceptable(keyClass, mapper);
	}

	/**
	 * <div lang="ja">
	 * 指定の{@link GSType}をロウキーの型とする合致条件を作成します。
	 *
	 * <p>合致条件の評価対象とするコンテナは、ロウキーを持ち、かつ、
	 * ロウキーの型が指定の{@link GSType}と同一の型でなければなりません。</p>
	 *
	 * <p>{@link #create(Class)}とは異なり、アプリケーションのコンパイル時点で
	 * ロウキーの型が確定しない場合の使用に適します。ただし、
	 * 条件内容を設定する際のロウキーの型チェックの基準は同一です。</p>
	 *
	 * <p>設定可能なロウキーの型は、{@link Container}のいずれかの
	 * サブインタフェースにて許容されている型のみです。</p>
	 *
	 * @param keyType 合致条件の評価対象とするロウキーの型
	 *
	 * @return 新規に作成された{@link RowKeyPredicate}
	 *
	 * @throws GSException 指定された型がロウキーとして常にサポート外となる場合
	 * @throws NullPointerException 引数に{@code null}が指定された場合
	 *
	 * @see Container
	 * </div><div lang="en">
	 * Creates an instance of {@link RowKeyPredicate} with the specified {@link GSType} as the {@link RowKey} type.
	 *
	 * <p>The target Container must have a {@link RowKey}, and the type of the {@link RowKey} must be
	 * the specified {@link GSType}</p>
	 *
	 * <p>Unlike {@link #create(Class)}, this method is used when the type of {@link RowKey} is not specified
	 * when the application is compiled. However, the criteria for checking the RowKey type when setting
	 * the condition is the same as {@link #create(Class)}.</p>
	 *
	 * <p>The type of {@link RowKey} that can be set is only that allowed
	 * by either one of the subinterfaces of {@link Container}.</p>
	 *
	 * @param keyType type of {@link RowKey} used as a search condition
	 *
	 * @return {@link RowKeyPredicate} newly created
	 *
	 * @throws GSException if the specified type is not always supported as a {@link RowKey}
	 * @throws NullPointerException if {@code null} is specified in the argument.
	 *
	 * @see Container
	 * </div>
	 */
	public static RowKeyPredicate<Object> create(
			GSType keyType) throws GSException {
		final Class<?> keyClass = SINGLE_CLASS_MAP.get(keyType);
		if (keyClass == null) {
			GSErrorCode.checkNullParameter(keyType, "keyType", null);
			throw new GSException(GSErrorCode.UNSUPPORTED_KEY_TYPE,
					"Unsupported key type (type=" + keyType + ")");
		}
		try {
			final RowMapper mapper = getSingleMapper(keyType);
			if (keyType == GSType.TIMESTAMP) {
				return new TimestampPredicate<Object>(
						Object.class, keyClass, mapper);
			}
			return new DefaultPredicate<Object>(
					Object.class, keyClass, mapper);
		}
		catch (GSException e) {
			throw new Error(e);
		}
	}

	/**
	 * <div lang="ja">
	 * 指定の{@link Class}に対応する{@link GSType}をロウキーの型とする
	 * 合致条件を作成します。
	 *
	 * <p>合致条件の評価対象とするコンテナは、単一カラムからなるロウキーを
	 * 持ち、かつ、そのロウキーの型は指定の{@link GSType}と同一の型でなければ
	 * なりません。</p>
	 *
	 * <p>設定可能なロウキーの型は、{@link Container}のいずれかの
	 * サブインタフェースにて許容されている型のみです。
	 * {@link Class}と{@link GSType}との対応関係については、
	 * {@link Container}の定義を参照してください。</p>
	 *
	 * <p>複合ロウキーなどロウキーを構成するカラムの個数によらずに合致条件を
	 * 作成するには、{@link #create(ContainerInfo)}を使用します。</p>
	 *
	 * @param keyType 合致条件の判定対象とするロウキーの型に対応する、{@link Class}
	 *
	 * @return 新規に作成された{@link RowKeyPredicate}
	 *
	 * @throws GSException 指定された型がロウキーとして常にサポート外となる場合
	 * @throws NullPointerException 引数に{@code null}が指定された場合
	 *
	 * @see Container
	 * </div><div lang="en">
	 * TODO Creates an instance of {@link RowKeyPredicate} with the {@link GSType} corresponding to
	 * the specified {@link Class} as the {@link RowKey} type.
	 *
	 * <p>The target Container must have a {@link RowKey}, and the type of the {@link RowKey} must be
	 * the specified {@link GSType}</p>
	 *
	 * <p>The type of {@link RowKey} that can be set is only that allowed
	 * by either one of the subinterfaces of {@link Container}.
	 * For the correspondence of {@link Class} to {@link GSType},
	 * see the definition of {@link Container}.</p>
	 *
	 * @param keyType {@link Class} corresponding to a {@link RowKey} used as a search condition
	 *
	 * @return {@link RowKeyPredicate} newly created
	 *
	 * @throws GSException if the specified type is not always supported as a {@link RowKey}
	 * @throws NullPointerException if {@code null} is specified in the argument.
	 *
	 * @see Container
	 * </div>
	 */
	public static <K> RowKeyPredicate<K> create(
			Class<K> keyType) throws GSException {
		final GSType gsKeyType = SINGLE_TYPE_MAP.get(keyType);
		if (gsKeyType == null) {
			GSErrorCode.checkNullParameter(keyType, "keyType", null);
			throw new GSException(GSErrorCode.UNSUPPORTED_KEY_TYPE,
					"Unsupported key type (type=" + keyType + ")");
		}
		try {
			final RowMapper mapper = getSingleMapper(gsKeyType);
			if (gsKeyType == GSType.TIMESTAMP) {
				return new TimestampPredicate<K>(keyType, keyType, mapper);
			}
			return new DefaultPredicate<K>(keyType, keyType, mapper);
		}
		catch (GSException e) {
			throw new Error(e);
		}
	}

	/**
	 * <div lang="ja">
	 * 指定の{@link ContainerInfo}のロウキーに関するカラム定義に基づく、
	 * 合致条件を作成します。
	 *
	 * <p>合致条件の評価対象とするコンテナは、ロウキーを持ち、かつ、指定の
	 * {@link ContainerInfo}のロウキーに関するカラム定義と対応づく
	 * 必要があります。ロウキー以外のカラム定義については対応関係の
	 * 判定に用いられません。</p>
	 *
	 * @param info 合致条件の判定対象とするロウキーのカラムレイアウトを含む、
	 * コンテナ情報。その他の内容は無視される
	 *
	 * @return 新規に作成された{@link RowKeyPredicate}
	 *
	 * @throws GSException 指定の情報がロウキーを含まないか、ロウキーとして
	 * 常にサポート外となる場合
	 * @throws NullPointerException 引数に{@code null}が指定された場合
	 *
	 * @since 4.3
	 * </div><div lang="en">
	 * TODO
	 *
	 * @since 4.3
	 * </div>
	 */
	public static RowKeyPredicate<Row.Key> create(
			ContainerInfo info) throws GSException {
		GSErrorCode.checkNullParameter(info, "info", null);
		final RowMapper mapper =
				RowMapper.getInstance(info.getType(), info, KEY_MAPPER_CONFIG);
		return new GeneralPredicate(mapper);
	}

	/**
	 * <div lang="ja">
	 * 範囲条件の開始位置とするロウキーの値を設定します。
	 *
	 * <p>設定された値より小さな値のロウキーは合致しないものとみなされる
	 * ようになります。</p>
	 *
	 * <p>STRING型のロウキーまたはその型を含む複合ロウキーのように、大小関係が
	 * 定義されていないロウキーの場合、条件として設定はできるものの、実際の
	 * 判定に用いることはできません。</p>
	 *
	 * @param startKey 開始位置とするロウキーの値。{@code null}の場合、
	 * 設定が解除される
	 *
	 * @throws GSException 個別条件がすでに設定されていた場合
	 * @throws ClassCastException 指定のロウキーの値の型が{@code null}
	 * ではなく、ロウキーに対応するクラスのインスタンスではない場合
	 * </div><div lang="en">
	 * TODO Sets the value of the {@link RowKey} at the starting positionof the range condition.
	 *
	 * <p>A {@link RowKey} with a value smaller than the specified value is deemed as non-conforming.</p>
	 *
	 * <p>A type with an undefined magnitude relationship can be set as a condition
	 * but cannot be used in the actual judgment, e.g. STRING type</p>
	 *
	 * @param startKey value of {@link RowKey} at the starting position or {@code null}.
	 * For {@code null}, the setting is cancelled.
	 *
	 * @throws GSException if an individual condition had been set already
	 * @throws ClassCastException the specified RowKey is not NULL or the type is not supported as {@link RowKey}
	 * </div>
	 */
	public void setStart(K startKey) throws GSException {
		if (distinctKeys != null) {
			throw new GSException(GSErrorCode.ILLEGAL_PARAMETER,
					"Distinct key has already been specified");
		}

		checkRangeKey(startKey);
		final K checkedKey = checkKeyType(startKey);

		start = duplicateKey(checkedKey, false);
	}

	/**
	 * <div lang="ja">
	 * 範囲条件の終了位置とするロウキーの値を設定します。
	 *
	 * <p>設定された値より大きな値のロウキーは合致しないものとみなされる
	 * ようになります。</p>
	 *
	 * <p>STRING型のロウキーまたはその型を含む複合ロウキーのように、大小関係が
	 * 定義されていないロウキーの場合、条件として設定はできるものの、実際の
	 * 判定に用いることはできません。</p>
	 *
	 * @param finishKey 終了位置とするロウキーの値。{@code null}の場合、
	 * 設定が解除される
	 *
	 * @throws GSException 個別条件がすでに設定されていた場合
	 * @throws ClassCastException 指定のロウキーの値の型が{@code null}
	 * ではなく、ロウキーに対応するクラスのインスタンスではない場合
	 * </div><div lang="en">
	 * TODO Sets the value of the {@link RowKey} at the last position of the range condition.
	 *
	 * <p>A {@link RowKey} with a value larger than the specified value is deemed as non-conforming.</p>
	 *
	 * <p>A type with an undefined magnitude relationship can be set as a condition
	 * but cannot be used in the actual judgment e.g. STRING type</p>
	 *
	 * @param finishKey the value of {@link RowKey} at the last position or {@code null}.
	 * For {@code null}, the setting is cancelled.
	 *
	 * @throws GSException if an individual condition had been set already
	 * @throws ClassCastException the value of specified key is not NULL
	 * or the type is not supported as {@link RowKey}
	 * </div>
	 */
	public void setFinish(K finishKey) throws GSException {
		if (distinctKeys != null) {
			throw new GSException(GSErrorCode.ILLEGAL_PARAMETER,
					"Distinct key has already been specified");
		}

		checkRangeKey(finishKey);
		final K checkedKey = checkKeyType(finishKey);

		finish = duplicateKey(checkedKey, false);
	}

	/**
	 * <div lang="ja">
	 * 個別条件の要素の一つとするロウキーの値を追加します。
	 *
	 * <p>追加された値と同一の値のロウキーは合致するものとみなされる
	 * ようになります。</p>
	 *
	 * @param key 個別条件の要素の一つとするロウキーの値。{@code null}は
	 * 指定できない
	 *
	 * @throws GSException 範囲条件がすでに設定されていた場合
	 * @throws ClassCastException 指定のロウキーの値の型が{@code null}
	 * ではなく、ロウキーに対応するクラスのインスタンスではない場合
	 * @throws NullPointerException 引数に{@code null}が指定された場合
	 * </div><div lang="en">
	 * Appends the value of the {@link RowKey} as one of the elements of the individual condition.
	 *
	 * <p>A {@link RowKey} with the same value as the added value is deemed as conforming.</p>
	 *
	 * @param key value of {@link RowKey} to be appended as one of the elements
	 * of the individual condition. Must not be a {@code null} value.
	 *
	 * @throws GSException if a range condition had already been set
	 * @throws ClassCastException the value of the specified key is not NULL or the type is not supported as {@link RowKey}
	 * @throws NullPointerException when {@code null} is specified as an argument
	 * </div>
	 */
	public void add(K key) throws GSException {
		if (start != null || finish != null) {
			throw new GSException(GSErrorCode.ILLEGAL_PARAMETER,
					"Start or finish key has already been specified");
		}

		GSErrorCode.checkNullParameter(key, "key", null);

		final K checkedKey = checkKeyType(key);

		Set<K> distinctKeys = this.distinctKeys;
		if (distinctKeys == null) {
			distinctKeys = new HashSet<K>();
		}

		distinctKeys.add(duplicateKey(checkedKey, true));
		this.distinctKeys = distinctKeys;
	}

	/**
	 * <div lang="ja">
	 * 複合ロウキーについての合致条件である場合を除いて、合致条件の評価対象
	 * とするロウキーの型を取得します。
	 *
	 * <p>複合ロウキーを含む任意のロウキーについてのスキーマを取得するには、
	 * {@link #getKeySchema()}}を使用します。</p>
	 *
	 * @return 合致条件の評価対象とするロウキーの型
	 *
	 * @throws IllegalStateException 複合ロウキーについての合致条件である
	 * 場合に呼び出された場合
	 * </div><div lang="en">
	 * TODO Returns the type of {@link RowKey} used as a search condition.
	 *
	 * @return the type of {@link RowKey} used as a search condition.
	 * </div>
	 */
	public GSType getKeyType() {
		if (mapper.getKeyCategory() != RowMapper.KeyCategory.SINGLE) {
			throw new IllegalStateException(
					"This method cannot be used for composite row key");
		}

		final GSType keyType = SINGLE_TYPE_MAP.get(keyClass);
		if (keyType == null) {
			throw new Error();
		}
		return keyType;
	}

	/**
	 * <div lang="ja">
	 * 合致条件の評価対象とするロウキーのスキーマを取得します。
	 *
	 * <p>この合致条件の作成に用いられた情報に、ロウキー以外のカラム情報や
	 * スキーマ以外のコンテナ情報が含まれていたとしても、返却されるスキーマ
	 * 情報には含まれません。</p>
	 *
	 * @return ロウキーのスキーマに関するコンテナ情報のみを持つ
	 * {@link ContainerInfo}
	 *
	 * @since 4.3
	 * </div><div lang="en">
	 * TODO
	 *
	 * @since 4.3
	 * </div>
	 */
	public ContainerInfo getKeySchema() {
		try {
			return mapper.resolveKeyContainerInfo();
		}
		catch (GSException e) {
			throw new Error(e);
		}
	}

	/**
	 * <div lang="ja">
	 * 範囲条件の開始位置とするロウキーの値を取得します。
	 *
	 * @return 開始位置とするロウキーの値。設定されていない場合は{@code null}
	 * </div><div lang="en">
	 * Returns the value of the {@link RowKey} at the starting position of the range condition.
	 *
	 * @return the value of {@link RowKey} at the starting position
	 * of the range condition, or {@code null} if it is not set.
	 * </div>
	 */
	public K getStart() {
		return duplicateKey(start, false);
	}

	/**
	 * <div lang="ja">
	 * 範囲条件の終了位置とするロウキーの値を取得します。
	 *
	 * @return 終了位置とするロウキーの値。設定されていない場合は{@code null}
	 * </div><div lang="en">
	 * Returns the value of {@link RowKey} at the last position
	 * of the range condition.
	 *
	 * @return the value of {@link RowKey} at the last position
	 * of the range condition, or {@code null} if it is not set.
	 * </div>
	 */
	public K getFinish() {
		return duplicateKey(finish, false);
	}

	/**
	 * <div lang="ja">
	 * 個別条件を構成するロウキーの値の集合を取得します。
	 *
	 * <p>返却された値に対して変更操作を行った場合に、
	 * {@link UnsupportedOperationException}などの実行時例外が発生するか
	 * どうかは未定義です。
	 * また、このオブジェクトに対する操作により、返却されたオブジェクトの内容が
	 * 変化するかどうかは未定義です。</p>
	 *
	 * @return 個別条件を構成するロウキーの値を要素とする
	 * {@link java.util.Collection}
	 * </div><div lang="en">
	 * Returns a Collection containing all of the values of the row keys
	 * that make up the individual condition.
	 *
	 * <p>It is not defined whether an exception like {@link UnsupportedOperationException}
	 * will occur during execution, when a returned object is updated.
	 * Moreover, after an object is returned, it is not defined
	 * whether an update of this object will change the contents of the returned object.</p>
	 *
	 * @return {@link java.util.Collection} containing all of the values of the row keys
	 * that make up the individual condition.
	 * </div>
	 */
	public java.util.Collection<K> getDistinctKeys() {
		if (distinctKeys == null) {
			return null;
		}

		final java.util.Collection<K> baseKeys;
		if (isImmutableKey()) {
			baseKeys = distinctKeys;
		}
		else {
			final List<K> keys = new ArrayList<K>(distinctKeys.size());
			for (K key : distinctKeys) {
				keys.add(duplicateKey(key, false));
			}
			baseKeys = keys;
		}
		return Collections.unmodifiableCollection(baseKeys);
	}

	Class<K> getBindingClass() {
		return bindingClass;
	}

	RowMapper getRowMapper() {
		return mapper;
	}

	K duplicateKey(K src, boolean identical) {
		if (src == null) {
			return null;
		}
		return duplicateKeyNoNull(src, identical);
	}

	K duplicateKeyNoNull(K src, boolean identical) {
		return src;
	}

	boolean isImmutableKey() {
		return true;
	}

	private void checkRangeKey(Object obj) throws GSException {
		if (obj == null) {
			return;
		}

		if (!rangeAcceptable) {
			throw new GSException(GSErrorCode.UNSUPPORTED_OPERATION,
					"String range is not supported");
		}
	}

	private K checkKeyType(K obj) throws GSException {
		if (obj == null) {
			return null;
		}

		if (bindingClass == Object.class) {
			if (obj instanceof Row.Key) {
				final Row.Key generalKey = (Row.Key) obj;
				final Object elemObj = generalKey.getValue(0);
				checkKeyType(elemObj, generalKey);
				return bindingClass.cast(elemObj);
			}
		}
		else if (bindingClass == Row.Key.class) {
			checkKeyType(obj, obj);
			return obj;
		}

		checkKeyType(obj, null);
		return obj;
	}

	private void checkKeyType(Object obj, Object generalObj)
			throws GSException {
		try {
			keyClass.cast(obj);
		}
		catch (ClassCastException cause) {
			final ClassCastException e =
					new ClassCastException("Row key class unmatched");
			e.initCause(cause);
			throw e;
		}

		if (generalObj != null) {
			final RowMapper objMapper;
			try {
				objMapper = RowMapper.getInstance(
						(Row) generalObj, KEY_MAPPER_CONFIG);
			}
			catch (GSException e) {
				throw new IllegalArgumentException(e);
			}

			try {
				objMapper.checkKeySchemaMatched(mapper);
			}
			catch (GSException e) {
				throw new GSException(
						"Row key schema unmatched", e);
			}
		}
	}

	private static RowMapper getSingleMapper(GSType keyType) {
		final RowMapper mapper = SINGLE_MAPPER_MAP.get(keyType);
		if (mapper == null) {
			throw new Error();
		}
		return mapper;
	}

	private static Map<GSType, RowMapper> makeSingleMapperMap() {
		final Map<GSType, RowMapper> map =
				new EnumMap<GSType, RowMapper>(GSType.class);
		for (GSType type : new GSType[] {
				GSType.STRING,
				GSType.INTEGER,
				GSType.LONG,
				GSType.TIMESTAMP
		}) {
			final boolean rowKeyAssigned = true;
			final ContainerInfo info = new ContainerInfo(
					null, null,
					Arrays.asList(new ColumnInfo(null, type)),
					rowKeyAssigned);

			final RowMapper mapper;
			try {
				mapper = RowMapper.getInstance(null, info, KEY_MAPPER_CONFIG);
			}
			catch (GSException e) {
				throw new Error(e);
			}

			map.put(type, mapper);
		}

		return map;
	}

	private static Map<GSType, Class<?>> makeSingleClassMap() {
		final Map<GSType, Class<?>> map =
				new EnumMap<GSType, Class<?>>(GSType.class);
		map.put(GSType.STRING, String.class);
		map.put(GSType.INTEGER, Integer.class);
		map.put(GSType.LONG, Long.class);
		map.put(GSType.TIMESTAMP, Date.class);
		return map;
	}

	private static Map<Class<?>, GSType> makeSingleTypeMap(
			Map<GSType, Class<?>> src) {
		final Map<Class<?>, GSType> map = new HashMap<Class<?>, GSType>();
		for (Map.Entry<GSType, Class<?>> entry : src.entrySet()) {
			map.put(entry.getValue(), entry.getKey());
		}
		return map;
	}

	private static void checkMapper(RowMapper mapper)
			throws GSException {
		if (!mapper.hasKey()) {
			throw new GSException(
					GSErrorCode.KEY_NOT_FOUND,
					"Row key does not exist on predicate for row key");
		}
	}

	private static boolean isRangeKeyAcceptable(
			Class<?> keyClass, RowMapper mapper) throws GSException {
		if (!stringRangeRestricted) {
			return true;
		}

		if (keyClass != Row.Key.class) {
			return (keyClass != String.class);
		}

		final ContainerInfo info = mapper.resolveKeyContainerInfo();
		final int columnCount = info.getColumnCount();
		for (int i = 0; i < columnCount; i++) {
			if (info.getColumnInfo(i).getType() == GSType.STRING) {
				return false;
			}
		}

		return true;
	}

	private static class DefaultPredicate<K>
	extends RowKeyPredicate<K> implements RowMapper.Provider {

		DefaultPredicate(
				Class<K> bindingClass, Class<?> keyClass, RowMapper mapper)
				throws GSException {
			super(bindingClass, keyClass, mapper);
		}

		@Override
		public RowMapper getRowMapper() {
			return super.getRowMapper();
		}

	}

	private static class GeneralPredicate extends DefaultPredicate<Row.Key> {

		GeneralPredicate(RowMapper mapper) throws GSException {
			super(Row.Key.class, Row.Key.class, mapper);
		}

		@Override
		Row.Key duplicateKeyNoNull(Row.Key src, boolean identical) {
			try {
				if (identical) {
					return RowMapper.createIdenticalRowKey(src);
				}
				else {
					return src.createKey();
				}
			}
			catch (GSException e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		boolean isImmutableKey() {
			return false;
		}

	}

	private static class TimestampPredicate<K> extends DefaultPredicate<K> {

		TimestampPredicate(
				Class<K> bindingClass, Class<?> keyClass, RowMapper mapper)
				throws GSException {
			super(bindingClass, keyClass, mapper);
		}

		@Override
		K duplicateKeyNoNull(K src, boolean identical) {
			return getBindingClass().cast(new Date(((Date) src).getTime())); 
		}

		@Override
		boolean isImmutableKey() {
			return false;
		}

	}

}
