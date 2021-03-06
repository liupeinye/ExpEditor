package com.example.expandableedittext;

import java.io.File;
import java.io.IOException;

import com.example.expandableedittext.MediaFile.MediaFileType;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.AlignmentSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

public class DroidWriterEditText extends EditText {

	// Log tag
	public static final String TAG = "DroidWriter";
	public static final int RESULT_LOAD_IMAGE = 0x01;
	public static final int RESULT_LOAD_VIDEO = 0x02;
	public static final int RESULT_LOAD_AUDIO = 0x03;

	// Style constants
	private static final int STYLE_BOLD = 0;
	private static final int STYLE_ITALIC = 1;
	private static final int STYLE_UNDERLINED = 2;

	// Align
	private static final int ALIGN_LEFT = 3;
	private static final int ALIGN_CENTER = 4;
	private static final int ALIGN_RIGHT = 5;

	// color
	private static final int COLOR = 6;
	private int color = 0xFF000000;
	private boolean isColorChange = false;

	// Optional styling button references
	private ToggleButton boldToggle; // 加粗
	private ToggleButton italicsToggle; // 斜体
	private ToggleButton underlineToggle;// 下划线

	// 有bug，还没实现，先留着
	private ToggleButton alignLeftToggle;// 左对齐
	private ToggleButton alignCenterToggle;// 中对齐
	private ToggleButton alignRightToggle;// 右对齐
	
	//music Path
	private static final String AUDIO_NAME = "/audio_test.mp4";
	
	private static final int imageMaxWidth = 100;
	
	private MediaRecorder mediaRecorder;

	// Html image getter that handles the loading of inline images
	private Html.ImageGetter imageGetter = new Html.ImageGetter() {
		
		@Override
		public Drawable getDrawable(String source) {
			Drawable drawable = null;
			if(source!=null)
			{
				Uri data  = Uri.parse(source);
				//类型判断
				ContentResolver cR = mActivity.getContentResolver();
				String mimeType = cR.getType(data);
				//image
				if(mimeType.startsWith("image")){
					String[] filePathColumn = { MediaStore.Images.Media.DATA };

					Cursor cursor = mActivity.getContentResolver().query(data,
							filePathColumn, null, null, null);
					cursor.moveToFirst();

					int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
					String picturePath = cursor.getString(columnIndex);
					cursor.close();

					drawable = new BitmapDrawable(mActivity.getResources(), SystemUtils.getImageThumbnail(picturePath, 300, 200));
					
					if(drawable !=null)
					{
						drawable.setBounds(0, 0, drawable.getMinimumWidth() , drawable.getMinimumHeight());
					}
				}else if(mimeType.startsWith("video")){
					//video
					String[] filePathColumn = { MediaStore.Video.Media.DATA };

					Cursor cursor = mActivity.getContentResolver().query(data,
							filePathColumn, null, null, null);
					cursor.moveToFirst();

					int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
					String videoPath = cursor.getString(columnIndex);
					cursor.close();

					drawable = new BitmapDrawable(mActivity.getResources(), SystemUtils.getVideoThumbnail(videoPath, 300, 200,MediaStore.Images.Thumbnails.MINI_KIND));
					
					if(drawable !=null)
					{
						drawable.setBounds(0, 0, drawable.getMinimumWidth() , drawable.getMinimumHeight());
					}
				}else if(mimeType.startsWith("audio")){
//					String[] filePathColumn = { MediaStore.Audio.Media.DATA };
//
//					Cursor cursor = mActivity.getContentResolver().query(data,
//							filePathColumn, null, null, null);
//					cursor.moveToFirst();

//					int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
//					String audioPath = cursor.getString(columnIndex);
//					cursor.close();

//					drawable = new BitmapDrawable(mActivity.getResources(), SystemUtils.getVideoThumbnail(audioPath, 300, 200,MediaStore.Images.Thumbnails.MINI_KIND));
					
					//从资源文件加载一张图片
					drawable = mActivity.getResources().getDrawable(R.drawable.icon);
					
					if(drawable !=null)
					{
						drawable.setBounds(0, 0, drawable.getMinimumWidth() , drawable.getMinimumHeight());
					}
				}
			}
			return drawable;
		}
	};

	private Activity mActivity;

	public DroidWriterEditText(Context context) {
		super(context);
		initialize(context);
	}

	public DroidWriterEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialize(context);
	}

	public DroidWriterEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialize(context);
	}

	private void initialize(Context context) {
		mActivity = (Activity) context;

		// Add TextWatcher that reacts to text changes and applies the selected
		// styles
		this.addTextChangedListener(new DWTextWatcher());
	}

	/**
	 * 判断是否有选中文字，若有，对其进行样式变换 When the user selects a section of the text, this
	 * method is used to toggle the defined style on it. If the selected text
	 * already has the style applied, we remove it, otherwise we apply it.
	 * 
	 * @param style
	 *            The styles that should be toggled on the selected text.
	 */
	public void toggleStyle(int style) {
		// Gets the current cursor position, or the starting position of the
		// selection
		int selectionStart = this.getSelectionStart();

		// Gets the current cursor position, or the end position of the
		// selection
		// Note: The end can be smaller than the start
		int selectionEnd = this.getSelectionEnd();
		
		System.out.println(selectionStart == selectionEnd);

		// Reverse if the case is what's noted above
		if (selectionStart > selectionEnd) {
			int temp = selectionEnd;
			selectionEnd = selectionStart;
			selectionStart = temp;
		}

		// The selectionEnd is only greater then the selectionStart position
		// when the user selected a section of the text. Otherwise, the 2
		// variables
		// should be equal (the cursor position).
		if (selectionEnd > selectionStart) {
			Spannable str = this.getText();
			boolean exists = false;
			StyleSpan[] styleSpans;

			switch (style) {
			case STYLE_BOLD:
				styleSpans = str.getSpans(selectionStart, selectionEnd,
						StyleSpan.class);

				// If the selected text-part already has BOLD style on it, then
				// we need to disable it
				for (int i = 0; i < styleSpans.length; i++) {
					if (styleSpans[i].getStyle() == android.graphics.Typeface.BOLD) {
						str.removeSpan(styleSpans[i]);
						exists = true;
					}
				}

				// Else we set BOLD style on it
				if (!exists) {
					str.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
							selectionStart, selectionEnd,
							Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
				}

				this.setSelection(selectionStart, selectionEnd);
				break;
			case STYLE_ITALIC:
				styleSpans = str.getSpans(selectionStart, selectionEnd,
						StyleSpan.class);

				// If the selected text-part already has ITALIC style on it,
				// then we need to disable it
				for (int i = 0; i < styleSpans.length; i++) {
					if (styleSpans[i].getStyle() == android.graphics.Typeface.ITALIC) {
						str.removeSpan(styleSpans[i]);
						exists = true;
					}
				}

				// Else we set ITALIC style on it
				if (!exists) {
					str.setSpan(
							new StyleSpan(android.graphics.Typeface.ITALIC),
							selectionStart, selectionEnd,
							Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
				}

				this.setSelection(selectionStart, selectionEnd);
				break;
			case STYLE_UNDERLINED:
				UnderlineSpan[] underSpan = str.getSpans(selectionStart,
						selectionEnd, UnderlineSpan.class);

				// If the selected text-part already has UNDERLINE style on it,
				// then we need to disable it
				for (int i = 0; i < underSpan.length; i++) {
					str.removeSpan(underSpan[i]);
					exists = true;
				}

				// Else we set UNDERLINE style on it
				if (!exists) {
					str.setSpan(new UnderlineSpan(), selectionStart,
							selectionEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
				}

				this.setSelection(selectionStart, selectionEnd);
				break;
			case COLOR:
				ForegroundColorSpan[] colorSpan = str.getSpans(selectionStart,
						selectionEnd, ForegroundColorSpan.class);

				// colorSpan[0].getForegroundColor()

				// If the selected text-part already has UNDERLINE style on it,
				// then we need to disable it
				for (int i = 0; i < colorSpan.length; i++) {
					str.removeSpan(colorSpan[i]);
					exists = true;
				}

				// Else we set UNDERLINE style on it
				if (!exists) {
					str.setSpan(new ForegroundColorSpan(color), selectionStart,
							selectionEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
				}

				this.setSelection(selectionStart, selectionEnd);
				break;
			}
		}
	}

	/**
	 * 系统回调方法 当选中时的时候进行判断是否全部为都是该属性，更新按钮的状态 This method makes sure that the
	 * optional style toggle buttons update their state correctly when the user
	 * moves the cursor around the EditText, or when the user selects sections
	 * of the text.
	 */
	@Override
	public void onSelectionChanged(int selStart, int selEnd) {
		boolean boldExists = false;
		boolean italicsExists = false;
		boolean underlinedExists = false;

		// If the user only placed the cursor around
		if (selStart > 0 && selStart == selEnd) {

			CharacterStyle[] styleSpans = this.getText().getSpans(selStart - 1,
					selStart, CharacterStyle.class);

			for (int i = 0; i < styleSpans.length; i++) {
				if (styleSpans[i] instanceof StyleSpan) {
					if (((StyleSpan) styleSpans[i]).getStyle() == android.graphics.Typeface.BOLD) {
						boldExists = true;
						System.out.println("BOLD");
					} else if (((StyleSpan) styleSpans[i]).getStyle() == android.graphics.Typeface.ITALIC) {
						italicsExists = true;
						System.out.println("ITALIC");
					} else if (((StyleSpan) styleSpans[i]).getStyle() == android.graphics.Typeface.BOLD_ITALIC) {
						italicsExists = true;
						boldExists = true;
						System.out.println("BOLD_ITALIC");
					}
				} else if (styleSpans[i] instanceof UnderlineSpan) {
					underlinedExists = true;
				}
			}
		}

		// Else if the user selected multiple characters
		else {
			CharacterStyle[] styleSpans = this.getText().getSpans(selStart,
					selEnd, CharacterStyle.class);

			for (int i = 0; i < styleSpans.length; i++) {
				if (styleSpans[i] instanceof StyleSpan) {
					if (((StyleSpan) styleSpans[i]).getStyle() == android.graphics.Typeface.BOLD) {
						if (this.getText().getSpanStart(styleSpans[i]) <= selStart
								&& this.getText().getSpanEnd(styleSpans[i]) >= selEnd) {
							boldExists = true;
						}
					} else if (((StyleSpan) styleSpans[i]).getStyle() == android.graphics.Typeface.ITALIC) {
						if (this.getText().getSpanStart(styleSpans[i]) <= selStart
								&& this.getText().getSpanEnd(styleSpans[i]) >= selEnd) {
							italicsExists = true;
						}
					} else if (((StyleSpan) styleSpans[i]).getStyle() == android.graphics.Typeface.BOLD_ITALIC) {
						if (this.getText().getSpanStart(styleSpans[i]) <= selStart
								&& this.getText().getSpanEnd(styleSpans[i]) >= selEnd) {
							italicsExists = true;
							boldExists = true;
						}
					}
				} else if (styleSpans[i] instanceof UnderlineSpan) {
					if (this.getText().getSpanStart(styleSpans[i]) <= selStart
							&& this.getText().getSpanEnd(styleSpans[i]) >= selEnd) {
						underlinedExists = true;
					}
				}
			}
		}

		// Display the format settings
		if (boldToggle != null) {
			if (boldExists)
				boldToggle.setChecked(true);
			else
				boldToggle.setChecked(false);
		}

		if (italicsToggle != null) {
			if (italicsExists)
				italicsToggle.setChecked(true);
			else
				italicsToggle.setChecked(false);
		}

		if (underlineToggle != null) {
			if (underlinedExists)
				underlineToggle.setChecked(true);
			else
				underlineToggle.setChecked(false);
		}
	}

	// Get and set Spanned, styled text
	public Spanned getSpannedText() {
		return this.getText();
	}

	public void setSpannedText(Spanned text) {
		this.setText(text);
	}

	// Get and set simple text as simple strings
	public String getStringText() {
		return this.getText().toString();
	}

	public void setStringText(String text) {
		this.setText(text);
	}

	// Get and set styled HTML text
	public String getTextHTML() {
		return Html.toHtml(this.getText());
	}

	public void setTextHTML(String text) {
		this.setText(Html.fromHtml(text, imageGetter, null));
	}

	// Style toggle button setters
	public void setBoldToggleButton(ToggleButton button) {
		boldToggle = button;

		boldToggle.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				toggleStyle(STYLE_BOLD);
			}
		});
	}

	public void setItalicsToggleButton(ToggleButton button) {
		italicsToggle = button;

		italicsToggle.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				toggleStyle(STYLE_ITALIC);
			}
		});
	}

	public void setUnderlineToggleButton(ToggleButton button) {
		underlineToggle = button;

		underlineToggle.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				toggleStyle(STYLE_UNDERLINED);
			}
		});
	}

	public void setLeftToggleButton(ToggleButton button) {
		alignLeftToggle = button;

		alignLeftToggle.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				toggleStyle(ALIGN_LEFT);
			}
		});
	}

	public void colorChange(int color) {
		this.color = color;
		isColorChange = true;
		toggleStyle(ALIGN_LEFT);
	}

	public void setCenterToggleButton(ToggleButton button) {
		alignCenterToggle = button;

		alignCenterToggle.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				toggleStyle(ALIGN_CENTER);
			}
		});
	}

	public void setRightToggleButton(ToggleButton button) {
		alignRightToggle = button;

		alignRightToggle.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				toggleStyle(ALIGN_RIGHT);
			}
		});
	}

	/**
	 * 插入图片  调起系统控件
	 * @author liupei
	 * @param button
	 */
	public void setImageInsertButton(View button) {
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent i = new Intent(
						Intent.ACTION_PICK,
						android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
				mActivity.startActivityForResult(i, RESULT_LOAD_IMAGE);
			}
		});
	}
	
	/**
	 * 插入视频  调起系统控件
	 * @param button
	 */
	public void setVideoInsertButton(View button){
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(
						Intent.ACTION_PICK,
						android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
				mActivity.startActivityForResult(i, RESULT_LOAD_VIDEO);
			}
		});
	}
	
	/**
	 * 插入音频  调起系统控件
	 * @param button
	 */
	public void setAudioInsertButton(View button){
		button.setOnTouchListener(new View.OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if((event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) && mediaRecorder == null){
					//开始录音
					Toast.makeText(mActivity, "startRecording", Toast.LENGTH_SHORT).show();
					startRecording();
					return true;
				}else if(event.getAction() == MotionEvent.ACTION_UP && mediaRecorder != null){
					//录音结束
					Toast.makeText(mActivity, "stopRecording", Toast.LENGTH_SHORT).show();
					stopRecording();
					Uri data = Uri.fromFile(new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/voice_records"+AUDIO_NAME));
					System.out.println(data);
					System.out.println(MediaFile.isAudioFileType(Environment.getExternalStorageDirectory().getAbsolutePath()+"/voice_records"+AUDIO_NAME));
					return true;
				}else if(event.getAction() == MotionEvent.ACTION_CANCEL && mediaRecorder != null){
					//录音结束 并 取消
					Toast.makeText(mActivity, "cancelRecording", Toast.LENGTH_SHORT).show();
					stopRecording();
					File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/voice_records"+AUDIO_NAME);
					if(f.exists())
						f.deleteOnExit();
					return true;
				}
				else
				return false;
			}
		});
	}

	public void setClearButton(View button) {
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				DroidWriterEditText.this.setText("");
			}
		});
	}
	
	/**
	 * activity回调事件   用于富媒体的收集
	 * @param requestCode
	 * @param resultCode
	 * @param data
	 */
	public void onResultForResolveRichMedia(int requestCode, int resultCode, Intent data){
		if ((requestCode == DroidWriterEditText.RESULT_LOAD_IMAGE || requestCode == DroidWriterEditText.RESULT_LOAD_VIDEO || requestCode == DroidWriterEditText.RESULT_LOAD_AUDIO)
				&& resultCode == Activity.RESULT_OK && data != null) {
			Uri richData = data.getData();

			int position = Selection
					.getSelectionStart(DroidWriterEditText.this.getText());
			
			Spanned e = Html.fromHtml(
					" <img src=\"" + richData + "\"/> ", imageGetter,
					null);
			DroidWriterEditText.this.getText().insert(position, e);
		}
	}
	
	private void startRecording(){
		mediaRecorder = new MediaRecorder();
		mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
		String path = Environment.getExternalStorageDirectory().getAbsolutePath()+"/voice_records/";
		File dir = new File(path);
		dir.mkdirs();
		mediaRecorder.setOutputFile(path+AUDIO_NAME);
		mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
		try {
			mediaRecorder.prepare();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		mediaRecorder.start();
	}
	
	private void stopRecording(){
		mediaRecorder.stop();
		mediaRecorder.reset();
		mediaRecorder.release();
		mediaRecorder = null;
	}

	private class DWTextWatcher implements TextWatcher {
		@Override
		public void afterTextChanged(Editable editable) {

			// Add style as the user types if a toggle button is enabled
			int position = Selection.getSelectionStart(DroidWriterEditText.this
					.getText());
			if (position < 0) {
				position = 0;
			}
			System.out.println(position);

			if (position > 0) {
				CharacterStyle[] appliedStyles = editable.getSpans(
						position - 1, position, CharacterStyle.class);// 样式
				AlignmentSpan.Standard[] alignstyles = editable.getSpans(
						position - 2, position, AlignmentSpan.Standard.class);// 布局

				StyleSpan currentBoldSpan = null;
				StyleSpan currentItalicSpan = null;
				UnderlineSpan currentUnderlineSpan = null;
				ForegroundColorSpan colorSpan = null;
				AlignmentSpan.Standard alignSpan = null;

				// Look for possible styles already applied to the entered text
				for (int i = 0; i < appliedStyles.length; i++) {
					if (appliedStyles[i] instanceof StyleSpan) {
						if (((StyleSpan) appliedStyles[i]).getStyle() == android.graphics.Typeface.BOLD) {
							// Bold style found
							currentBoldSpan = (StyleSpan) appliedStyles[i];
						} else if (((StyleSpan) appliedStyles[i]).getStyle() == android.graphics.Typeface.ITALIC) {
							// Italic style found
							currentItalicSpan = (StyleSpan) appliedStyles[i];
						} else if (((StyleSpan) appliedStyles[i]).getStyle() == android.graphics.Typeface.BOLD_ITALIC) {
							// Italic&Bold style found
							currentItalicSpan = (StyleSpan) appliedStyles[i];
							currentBoldSpan = (StyleSpan) appliedStyles[i];
						}
					} else if (appliedStyles[i] instanceof UnderlineSpan) {
						// Underlined style found
						currentUnderlineSpan = (UnderlineSpan) appliedStyles[i];
					} else if (appliedStyles[i] instanceof ForegroundColorSpan) {
						colorSpan = (ForegroundColorSpan) appliedStyles[i];
					}
				}

				if (alignstyles.length > 0) {
					alignSpan = alignstyles[0];
				}

				// 颜色变化
				if (isColorChange) {
					isColorChange = false;
					if (colorSpan == null) {// 原本没有span时，直接加入
						editable.setSpan(new ForegroundColorSpan(color),
								position - 1, position,
								Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
					} else {// 原来有span时，记住原来span的起始和终点位置，先消除，再为其加上旧颜色。游标开始的位置使用新的
						int colorStart = editable.getSpanStart(colorSpan);
						int colorEnd = editable.getSpanEnd(colorSpan);
						int oldcolor = colorSpan.getForegroundColor();
						editable.removeSpan(colorSpan);
						if (colorStart <= (position - 1)) {
							editable.setSpan(new ForegroundColorSpan(oldcolor),
									colorStart, position - 1,
									Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
						}

						if (colorEnd > position) {
							editable.setSpan(new ForegroundColorSpan(oldcolor),
									position, colorEnd,
									Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
						}
						editable.setSpan(new ForegroundColorSpan(color),
								position - 1, position,
								Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
					}
				}

				if (alignCenterToggle != null) {
					if (alignCenterToggle.isChecked() && alignSpan == null) {
						editable.setSpan(new AlignmentSpan.Standard(
								Layout.Alignment.ALIGN_CENTER), position - 1,
								position, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					} else if (!alignCenterToggle.isChecked()
							&& alignSpan != null
							&& alignSpan.getAlignment().compareTo(
									Layout.Alignment.ALIGN_CENTER) == 0) {
						int alignStart = editable.getSpanStart(alignSpan);
						int alignEnd = editable.getSpanEnd(alignSpan);

						editable.removeSpan(alignSpan);
						if (alignStart <= (position - 1)) {
							editable.setSpan(new AlignmentSpan.Standard(
									Layout.Alignment.ALIGN_CENTER), alignStart,
									position - 1,
									Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
						}

						// The old bold style span end after the current cursor
						// position, so we need to define a
						// second newly created style span too, which begins
						// after the newly entered character and
						// ends at the old span's ending position. So we split
						// the span.
						if (alignEnd > position) {
							editable.setSpan(new AlignmentSpan.Standard(
									Layout.Alignment.ALIGN_CENTER), position,
									alignEnd,
									Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
						}
					}
				}

				if (alignRightToggle != null) {
					if (alignRightToggle.isChecked() && alignSpan == null) {
						editable.setSpan(new AlignmentSpan.Standard(
								Layout.Alignment.ALIGN_OPPOSITE), position - 1,
								position, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					} else if (!alignRightToggle.isChecked()
							&& alignSpan != null
							&& alignSpan.getAlignment().compareTo(
									Layout.Alignment.ALIGN_OPPOSITE) == 0) {
						int alignStart = editable.getSpanStart(alignSpan);
						int alignEnd = editable.getSpanEnd(alignSpan);

						editable.removeSpan(alignSpan);
						if (alignStart <= (position - 1)) {
							editable.setSpan(new AlignmentSpan.Standard(
									Layout.Alignment.ALIGN_OPPOSITE),
									alignStart, position - 1,
									Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
						}

						// The old bold style span end after the current cursor
						// position, so we need to define a
						// second newly created style span too, which begins
						// after the newly entered character and
						// ends at the old span's ending position. So we split
						// the span.
						if (alignEnd > position) {
							editable.setSpan(new AlignmentSpan.Standard(
									Layout.Alignment.ALIGN_OPPOSITE), position,
									alignEnd,
									Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
						}
					}
				}

				// Handle the bold style toggle button if it's present
				if (boldToggle != null) {
					if (boldToggle.isChecked() && currentBoldSpan == null) {
						// The user switched the bold style button on and the
						// character doesn't have any bold
						// style applied, so we start a new bold style span. The
						// span is inclusive,
						// so any new characters entered right after this one
						// will automatically get this style.
						editable.setSpan(new StyleSpan(
								android.graphics.Typeface.BOLD), position - 1,
								position, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
					} else if (!boldToggle.isChecked()
							&& currentBoldSpan != null) {
						// The user switched the bold style button off and the
						// character has bold style applied.
						// We need to remove the old bold style span, and define
						// a new one that end 1 position right
						// before the newly entered character.
						int boldStart = editable.getSpanStart(currentBoldSpan);
						int boldEnd = editable.getSpanEnd(currentBoldSpan);

						editable.removeSpan(currentBoldSpan);
						if (boldStart <= (position - 1)) {
							editable.setSpan(new StyleSpan(
									android.graphics.Typeface.BOLD), boldStart,
									position - 1,
									Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
						}

						// The old bold style span end after the current cursor
						// position, so we need to define a
						// second newly created style span too, which begins
						// after the newly entered character and
						// ends at the old span's ending position. So we split
						// the span.
						if (boldEnd > position) {
							editable.setSpan(new StyleSpan(
									android.graphics.Typeface.BOLD), position,
									boldEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
						}
					}
				}

				// Handling italics and underlined styles is the same as
				// handling bold styles.

				// Handle the italics style toggle button if it's present
				if (italicsToggle != null && italicsToggle.isChecked()
						&& currentItalicSpan == null) {
					editable.setSpan(new StyleSpan(
							android.graphics.Typeface.ITALIC), position - 1,
							position, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
				} else if (italicsToggle != null && !italicsToggle.isChecked()
						&& currentItalicSpan != null) {
					int italicStart = editable.getSpanStart(currentItalicSpan);
					int italicEnd = editable.getSpanEnd(currentItalicSpan);

					editable.removeSpan(currentItalicSpan);
					if (italicStart <= (position - 1)) {
						editable.setSpan(new StyleSpan(
								android.graphics.Typeface.ITALIC), italicStart,
								position - 1,
								Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
					}

					// Split the span
					if (italicEnd > position) {
						editable.setSpan(new StyleSpan(
								android.graphics.Typeface.ITALIC), position,
								italicEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
					}
				}

				// Handle the underlined style toggle button if it's present
				if (underlineToggle != null && underlineToggle.isChecked()
						&& currentUnderlineSpan == null) {
					editable.setSpan(new UnderlineSpan(), position - 1,
							position, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
				} else if (underlineToggle != null
						&& !underlineToggle.isChecked()
						&& currentUnderlineSpan != null) {
					int underLineStart = editable
							.getSpanStart(currentUnderlineSpan);
					int underLineEnd = editable
							.getSpanEnd(currentUnderlineSpan);

					editable.removeSpan(currentUnderlineSpan);
					if (underLineStart <= (position - 1)) {
						editable.setSpan(new UnderlineSpan(), underLineStart,
								position - 1,
								Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
					}

					// We need to split the span
					if (underLineEnd > position) {
						editable.setSpan(new UnderlineSpan(), position,
								underLineEnd,
								Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
					}
				}
			}
		}

		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
			// Unused
		}

		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			// Unused
		}
	}
}
